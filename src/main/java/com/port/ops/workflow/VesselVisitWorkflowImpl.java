package com.port.ops.workflow;

import com.port.ops.TaskQueues;
import com.port.ops.activity.CraneActivities;
import com.port.ops.activity.PlannerActivities;
import com.port.ops.model.MoveList;
import com.port.ops.model.MoveListAccepted;
import com.port.ops.model.OpsTimestamp;
import com.port.ops.model.OpsTimestamp.Milestone;
import com.port.ops.model.OpsTimestamp.Type;
import com.port.ops.model.VisitPhase;
import com.port.ops.model.VisitProgress;
import com.port.ops.model.VisitProgress.CraneProgress;
import com.port.ops.model.VisitResult;
import com.port.ops.model.VisitSettings;
import com.port.ops.model.VisitStart;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The visit, written as if nothing ever failed and no process ever restarted.
 * Temporal makes that true: every activity result, timer and message is
 * recorded, and after a crash a new worker replays this code against the
 * record to rebuild the fields below exactly as they were.
 *
 * Not a Spring bean. The starter registers the class with the worker for
 * {@link TaskQueues#VESSEL_VISITS}, and Temporal creates one instance per
 * visit: the fields are that visit's state.
 */
@WorkflowImpl(taskQueues = TaskQueues.VESSEL_VISITS)
public class VesselVisitWorkflowImpl implements VesselVisitWorkflow {

    /** Replay-aware: does not log again when a worker replays history. */
    private static final Logger log = Workflow.getLogger(VesselVisitWorkflowImpl.class);

    static final String CRANE_BREAKDOWN = "CraneBreakdown";

    /*
     * Crane work goes to the quay-cranes queue. The heartbeat timeout is short
     * so that a dead worker is noticed within seconds, not after the whole
     * start-to-close timeout. There is no limit on attempts: a crane control
     * system that is down for a while should delay the visit, not fail it.
     * A breakdown is not retried because the activity marks it non-retryable.
     */
    private final CraneActivities cranes = Workflow.newActivityStub(CraneActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.QUAY_CRANES)
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .setHeartbeatTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2)
                            .setMaximumInterval(Duration.ofSeconds(20))
                            .build())
                    .build());

    /* No task queue given: the activities run on the workflow's own queue, vessel-visits. */
    private final PlannerActivities planner = Workflow.newActivityStub(PlannerActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build());

    private final VisitStart start;
    private final VisitSettings settings;
    private final TerminalClock clock;

    private VisitPhase phase = VisitPhase.AWAITING_MOVE_LIST;
    private MoveList moveList;
    private Long berthedAtRealMillis;
    private long cargoStartRealMillis;
    private Instant plannedCompletion;
    private boolean overrunReported;
    private String failure;

    /* Linked collections: iteration order must be the same on every replay. */
    private final Map<String, CraneState> craneStates = new LinkedHashMap<>();
    private final Map<String, OpsTimestamp> timestamps = new LinkedHashMap<>();
    private final Set<String> busyCranes = new LinkedHashSet<>();
    private final List<Promise<Void>> craneLoops = new ArrayList<>();

    /**
     * Runs before any update, signal or query is delivered, so the handlers
     * below can rely on {@code start} and the clock. Without it, a message
     * that arrives in the very first workflow task could reach a handler
     * before {@link #run} has set the fields.
     */
    @WorkflowInit
    public VesselVisitWorkflowImpl(VisitStart start) {
        this.start = start;
        this.settings = start.settings();
        this.clock = TerminalClock.startingNow(settings.timeScale());
    }

    @Override
    public VisitResult run(VisitStart start) {
        // 1. A durable timer raced against a condition. The timer lives on the
        //    server, so it fires on time even if no worker is running then.
        Duration deadline = Duration.ofMinutes(settings.moveListDeadlineMinutes());
        if (!Workflow.await(clock.real(deadline), () -> moveList != null)) {
            throw fail("MoveListMissing", "No move list within " + deadline.toHours()
                    + " terminal hours of the berth being confirmed");
        }

        // 2. No timeout: a ship can be late by days and the visit just waits.
        phase = VisitPhase.AWAITING_BERTHING;
        Workflow.await(() -> berthedAtRealMillis != null);
        Instant ata = clock.at(berthedAtRealMillis);
        stamp(Milestone.ARRIVAL_BERTH, Type.ACT, ata);
        plannedCompletion = ata.plus(Duration.ofMinutes(Math.round(start.plannedBerthHours() * 60)));
        stamp(Milestone.CARGO_OPS_COMPLETE, Type.PLN, plannedCompletion);
        stamp(Milestone.DEPARTURE_BERTH, Type.PLN, plannedCompletion.plus(departureAfter()));

        // 3. Cranes. Released in finally, so a visit that fails gives them back.
        int wanted = CraneSplit.craneCount(moveList.totalMoves(), settings.movesPerCrane(), settings.maxCranes());
        List<String> reserved = cranes.reserveCranes(start.visitId(), wanted);
        try {
            workTheShip(reserved);
        } finally {
            cranes.releaseCranes(start.visitId());
        }

        // 4. Cargo done: tell the planner how long it really took.
        Instant cargoStarted = clock.at(cargoStartRealMillis);
        Instant completed = clock.now();
        long handlingMinutes = Duration.between(cargoStarted, completed).toMinutes();
        stamp(Milestone.CARGO_OPS_COMPLETE, Type.ACT, completed);
        stamp(Milestone.DEPARTURE_BERTH, Type.EST, completed.plus(departureAfter()));
        phase = VisitPhase.COMPLETED;
        planner.reportCompletion(start.visitId(), start.berthCode(), cargoStarted, completed, handlingMinutes);

        // 5. Lashing and the pilot, then the ship sails and the berth is free.
        Workflow.sleep(clock.real(departureAfter()));
        Instant departed = clock.now();
        stamp(Milestone.DEPARTURE_BERTH, Type.ACT, departed);
        planner.releaseBerth(start.visitId(), start.berthCode(), departed);
        phase = VisitPhase.DEPARTED;

        return new VisitResult(start.visitId(), moveList.totalMoves(), reserved,
                craneStates.values().stream().filter(c -> c.brokenDown).map(c -> c.craneId).toList(),
                handlingMinutes, overrunReported);
    }

    @Override
    public void validateMoveList(MoveList moveList) {
        // Read-only: a validator must not change state or call activities.
        if (phase != VisitPhase.AWAITING_MOVE_LIST && phase != VisitPhase.AWAITING_BERTHING) {
            throw new IllegalStateException("Cargo work has started; the move list can no longer change");
        }
        moveList.check();
    }

    @Override
    public MoveListAccepted submitMoveList(MoveList moveList) {
        // The line may send a revised list until the cranes start; the last one wins.
        this.moveList = moveList;
        int planned = CraneSplit.craneCount(moveList.totalMoves(), settings.movesPerCrane(), settings.maxCranes());
        log.info("Move list accepted for {}: {} moves in {} bays", start.visitId(),
                moveList.totalMoves(), moveList.bays().size());
        return new MoveListAccepted(moveList.bays().size(), moveList.totalMoves(), planned);
    }

    @Override
    public void vesselBerthed() {
        // A signal cannot be refused, so a repeated one is simply ignored.
        if (berthedAtRealMillis == null) {
            berthedAtRealMillis = Workflow.currentTimeMillis();
        }
    }

    @Override
    public VisitProgress progress() {
        // Queries must not change state or block: build a snapshot and return it.
        List<CraneProgress> craneProgress = craneStates.values().stream()
                .map(c -> new CraneProgress(c.craneId, c.status(),
                        c.baysLeft(), c.movesDone, c.remainingMoves()))
                .toList();
        int movesDone = craneStates.values().stream().mapToInt(c -> c.movesDone).sum();
        return new VisitProgress(start.visitId(), start.vesselName(), start.berthCode(), phase,
                moveList == null ? null : moveList.totalMoves(), movesDone, craneProgress,
                List.copyOf(timestamps.values()), overrunReported, failure);
    }

    /** One coroutine per crane, all working at once; returns when every bay is done. */
    private void workTheShip(List<String> reserved) {
        CraneSplit.split(moveList.bays(), reserved)
                .forEach((crane, bays) -> craneStates.put(crane, new CraneState(crane, bays)));
        cargoStartRealMillis = Workflow.currentTimeMillis();
        stamp(Milestone.CARGO_OPS_START, Type.ACT, clock.now());
        phase = VisitPhase.WORKING;
        updateEstimate();

        craneStates.keySet().forEach(this::startCrane);
        Workflow.await(busyCranes::isEmpty);
        // A crane loop that failed (every crane broken down) fails the visit here.
        craneLoops.forEach(Promise::get);
    }

    /*
     * Async.procedure is the workflow's own kind of thread. It runs on
     * Temporal's deterministic scheduler, one coroutine at a time, so the
     * loops share these fields without locks. A real Java thread here would
     * break replay.
     */
    private void startCrane(String craneId) {
        busyCranes.add(craneId);
        craneLoops.add(Async.procedure(this::workCrane, craneId));
    }

    private void workCrane(String craneId) {
        CraneState crane = craneStates.get(craneId);
        try {
            while (!crane.queue.isEmpty()) {
                crane.current = crane.queue.pollFirst();
                try {
                    cranes.workBay(start.visitId(), craneId, crane.current.bay(), crane.current.moves());
                    crane.movesDone += crane.current.moves();
                    crane.current = null;
                } catch (ActivityFailure e) {
                    if (!(e.getCause() instanceof ApplicationFailure cause) || !CRANE_BREAKDOWN.equals(cause.getType())) {
                        throw e;
                    }
                    // The activity said how far it got; only the rest of the bay moves on.
                    int done = cause.getDetails().get(Integer.class);
                    BayWork rest = new BayWork(crane.current.bay(), crane.current.moves() - done);
                    crane.movesDone += done;
                    crane.current = null;
                    crane.brokenDown = true;
                    if (rest.moves() > 0) {
                        crane.queue.addFirst(rest);
                    }
                    handOver(crane);
                    return;
                }
                updateEstimate();
            }
        } finally {
            busyCranes.remove(craneId);
        }
    }

    /**
     * Gives a broken-down crane's bays to its nearest working neighbour on the
     * rail, the less loaded one on a tie. Bays from a crane to the right go to
     * the end of the neighbour's list, bays from the left to the front, so each
     * crane still works along the ship in one direction.
     */
    private void handOver(CraneState broken) {
        List<String> rail = new ArrayList<>(craneStates.keySet());
        int at = rail.indexOf(broken.craneId);
        CraneState target = craneStates.values().stream()
                .filter(c -> !c.brokenDown)
                .min(Comparator.comparingInt((CraneState c) -> Math.abs(rail.indexOf(c.craneId) - at))
                        .thenComparingInt(CraneState::remainingMoves))
                .orElse(null);
        if (target == null) {
            throw fail("AllCranesDown", "Every crane on " + start.vesselName() + " has broken down with "
                    + broken.remainingMoves() + " moves left");
        }
        List<BayWork> moved = new ArrayList<>(broken.queue);
        broken.queue.clear();
        if (rail.indexOf(target.craneId) < at) {
            target.queue.addAll(moved);
        } else {
            for (int i = moved.size() - 1; i >= 0; i--) {
                target.queue.addFirst(moved.get(i));
            }
        }
        log.info("{} broke down; {} takes over {} bays", broken.craneId, target.craneId, moved.size());
        if (!busyCranes.contains(target.craneId)) {
            startCrane(target.craneId);
        }
        updateEstimate();
    }

    /** Recomputes the ETC after every bay, and warns the planner once if the ship will overrun. */
    private void updateEstimate() {
        Instant now = clock.now();
        double hoursWorked = Duration.between(clock.at(cargoStartRealMillis), now).toMillis() / 3_600_000.0;
        Instant etc = EtcCalculator.estimate(now, hoursWorked, settings.movesPerHour(),
                craneStates.values().stream()
                        .filter(c -> !c.brokenDown)
                        .map(c -> new EtcCalculator.CraneWork(c.movesDone, c.remainingMoves()))
                        .toList());
        stamp(Milestone.CARGO_OPS_COMPLETE, Type.EST, etc);
        stamp(Milestone.DEPARTURE_BERTH, Type.EST, etc.plus(departureAfter()));

        Duration late = Duration.between(plannedCompletion, etc);
        if (!overrunReported && late.compareTo(Duration.ofMinutes(settings.overrunThresholdMinutes())) > 0) {
            // Set before the call: other crane loops run while this one waits for the activity.
            overrunReported = true;
            planner.reportOverrun(start.visitId(), start.berthCode(), plannedCompletion, etc);
        }
    }

    private Duration departureAfter() {
        return Duration.ofMinutes(settings.departureAfterCompletionMinutes());
    }

    private void stamp(Milestone milestone, Type type, Instant time) {
        timestamps.put(milestone + "/" + type, new OpsTimestamp(milestone, type, time));
    }

    /** Fails the workflow. The phase and reason stay visible to the progress query afterwards. */
    private ApplicationFailure fail(String type, String message) {
        phase = VisitPhase.FAILED;
        failure = message;
        return ApplicationFailure.newFailure(message, type);
    }

    /** One crane's share of the ship. Plain mutable state; the workflow is its only user. */
    private static final class CraneState {
        final String craneId;
        final Deque<BayWork> queue;
        BayWork current;
        int movesDone;
        boolean brokenDown;

        CraneState(String craneId, List<BayWork> bays) {
            this.craneId = craneId;
            this.queue = new ArrayDeque<>(bays);
        }

        int remainingMoves() {
            int queued = queue.stream().mapToInt(BayWork::moves).sum();
            return queued + (current == null ? 0 : current.moves());
        }

        List<Integer> baysLeft() {
            List<Integer> bays = new ArrayList<>();
            if (current != null) {
                bays.add(current.bay());
            }
            queue.forEach(b -> bays.add(b.bay()));
            return bays;
        }

        String status() {
            if (brokenDown) {
                return "BROKEN_DOWN";
            }
            return current != null ? "WORKING" : queue.isEmpty() ? "DONE" : "WAITING";
        }
    }
}
