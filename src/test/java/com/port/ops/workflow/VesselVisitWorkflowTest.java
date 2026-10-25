package com.port.ops.workflow;

import com.port.ops.TaskQueues;
import com.port.ops.activity.CraneActivities;
import com.port.ops.activity.PlannerActivities;
import com.port.ops.model.MoveList;
import com.port.ops.model.MoveList.BayMoves;
import com.port.ops.model.MoveListAccepted;
import com.port.ops.model.VisitPhase;
import com.port.ops.model.VisitProgress;
import com.port.ops.model.VisitResult;
import com.port.ops.model.VisitSettings;
import com.port.ops.model.VisitStart;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The workflow against an in-memory Temporal server with <b>time skipping</b>:
 * whenever every workflow is waiting on a timer, the test server jumps its
 * clock forward. The 24-hour move list deadline and the departure wait take
 * no real time at all.
 *
 * The activities are fakes that answer at once, so these tests are about the
 * workflow's decisions, not the crane simulation.
 */
class VesselVisitWorkflowTest {

    private static final VisitSettings SETTINGS = new VisitSettings(360, 30, 400, 3, 24 * 60, 60, 60);

    private TestWorkflowEnvironment env;
    private FakeCranes cranes;
    private FakePlanner planner;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        cranes = new FakeCranes();
        planner = new FakePlanner();
        // Two workers, as in the application: one per task queue.
        Worker visits = env.newWorker(TaskQueues.VESSEL_VISITS);
        visits.registerWorkflowImplementationTypes(VesselVisitWorkflowImpl.class);
        visits.registerActivitiesImplementations(planner);
        env.newWorker(TaskQueues.QUAY_CRANES).registerActivitiesImplementations(cranes);
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void aVisitRunsFromBerthConfirmedToDeparture() {
        VesselVisitWorkflow visit = startVisit(11);

        MoveListAccepted accepted = visit.submitMoveList(twelveBays());
        assertThat(accepted.totalMoves()).isEqualTo(1020);
        assertThat(accepted.plannedCranes()).isEqualTo(3);
        assertThat(visit.progress().phase()).isEqualTo(VisitPhase.AWAITING_BERTHING);

        visit.vesselBerthed();
        VisitResult result = WorkflowStub.fromTyped(visit).getResult(VisitResult.class);

        assertThat(result.cranesUsed()).containsExactly("QC01", "QC02", "QC03");
        assertThat(cranes.totalMoves()).isEqualTo(1020);
        assertThat(cranes.released).isTrue();
        assertThat(planner.calls).containsExactly("reportCompletion", "releaseBerth");
        assertThat(visit.progress().phase()).isEqualTo(VisitPhase.DEPARTED);
    }

    @Test
    void anInvalidMoveListIsRejectedAndTheVisitKeepsWaiting() {
        VesselVisitWorkflow visit = startVisit(11);

        MoveList duplicateBay = new MoveList(List.of(new BayMoves(2, 10, 10), new BayMoves(2, 5, 5)));
        WorkflowUpdateException rejected = assertThrows(WorkflowUpdateException.class,
                () -> visit.submitMoveList(duplicateBay));

        assertThat(((ApplicationFailure) rejected.getCause()).getOriginalMessage()).isEqualTo("Bay 2 appears twice");
        assertThat(visit.progress().phase()).isEqualTo(VisitPhase.AWAITING_MOVE_LIST);
    }

    @Test
    void noMoveListByTheDeadlineFailsTheVisit() {
        VesselVisitWorkflow visit = startVisit(11);

        // Time skipping: the 24-hour (terminal) timer fires as soon as nothing else can happen.
        WorkflowFailedException failed = assertThrows(WorkflowFailedException.class,
                () -> WorkflowStub.fromTyped(visit).getResult(VisitResult.class));

        assertThat(((ApplicationFailure) failed.getCause()).getType()).isEqualTo("MoveListMissing");
        assertThat(cranes.reserved).isFalse();
    }

    @Test
    void aBrokenDownCraneHandsItsBaysToANeighbour() {
        cranes.breakDown("QC02", 10);
        VesselVisitWorkflow visit = startVisit(11);
        visit.submitMoveList(twelveBays());
        visit.vesselBerthed();

        VisitResult result = WorkflowStub.fromTyped(visit).getResult(VisitResult.class);

        assertThat(result.cranesBrokenDown()).containsExactly("QC02");
        // Every move done exactly once: the 10 QC02 lifted before it broke are not lifted again.
        assertThat(cranes.totalMoves()).isEqualTo(1020);
        assertThat(cranes.movesBy.get("QC02")).isEqualTo(10);
    }

    @Test
    void aShipThatWillFinishLateIsReportedToThePlannerOnce() {
        // 3 cranes at 30 moves an hour need about 11.3 hours; 9 are planned.
        VesselVisitWorkflow visit = startVisit(9);
        visit.submitMoveList(twelveBays());
        visit.vesselBerthed();

        VisitResult result = WorkflowStub.fromTyped(visit).getResult(VisitResult.class);

        assertThat(result.overrunReported()).isTrue();
        assertThat(planner.calls).containsExactly("reportOverrun", "reportCompletion", "releaseBerth");
    }

    private VesselVisitWorkflow startVisit(double plannedBerthHours) {
        VesselVisitWorkflow visit = env.getWorkflowClient().newWorkflowStub(VesselVisitWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("visit-9321483-V042")
                        .setTaskQueue(TaskQueues.VESSEL_VISITS)
                        .build());
        WorkflowClient.start(visit::run, new VisitStart("visit-9321483-V042", "9321483", "V042",
                "NORDIC STAR", "B1", plannedBerthHours, SETTINGS));
        return visit;
    }

    /** Bays 2, 6, 10 ... 46: 40 off and 45 on each, 1,020 moves in all. */
    private static MoveList twelveBays() {
        return new MoveList(IntStream.range(0, 12).mapToObj(i -> new BayMoves(2 + 4 * i, 40, 45)).toList());
    }

    /** Answers at once; can break one crane down part way through its first bay. */
    static class FakeCranes implements CraneActivities {
        final Map<String, Integer> movesBy = new ConcurrentHashMap<>();
        volatile boolean reserved;
        volatile boolean released;
        private String breaksDown;
        private int breaksAfter;

        void breakDown(String craneId, int afterMoves) {
            breaksDown = craneId;
            breaksAfter = afterMoves;
        }

        @Override
        public List<String> reserveCranes(String visitId, int count) {
            reserved = true;
            return List.of("QC01", "QC02", "QC03", "QC04").subList(0, count);
        }

        @Override
        public void workBay(String visitId, String craneId, int bay, int moves) {
            if (craneId.equals(breaksDown)) {
                breaksDown = null;
                movesBy.merge(craneId, breaksAfter, Integer::sum);
                throw ApplicationFailure.newNonRetryableFailure(craneId + " broke down", "CraneBreakdown", breaksAfter);
            }
            movesBy.merge(craneId, moves, Integer::sum);
        }

        @Override
        public void releaseCranes(String visitId) {
            released = true;
        }

        int totalMoves() {
            return movesBy.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    static class FakePlanner implements PlannerActivities {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void reportOverrun(String visitId, String berthCode, Instant plannedCompletion,
                                  Instant estimatedCompletion) {
            calls.add("reportOverrun");
        }

        @Override
        public void reportCompletion(String visitId, String berthCode, Instant cargoOpsStarted,
                                     Instant cargoOpsCompleted, long handlingMinutes) {
            calls.add("reportCompletion");
        }

        @Override
        public void releaseBerth(String visitId, String berthCode, Instant departed) {
            calls.add("releaseBerth");
        }
    }
}
