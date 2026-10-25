package com.port.ops.sim;

import com.port.ops.config.OpsProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pretend crane control system with four quay cranes on one rail. It is
 * slow to answer, sometimes fails ({@code ops.sim.failure-rate}) and can
 * break a crane down ({@code ops.sim.breakdown-rate}), so you can watch
 * Temporal's retries and the workflow's recovery.
 *
 * In memory: a restart forgets every reservation. A real crane system would
 * remember them, so a restarted worker would find its cranes still held.
 */
@Component
public class CraneSystem {

    /** Each crane's speed against the planning figure. QC02 is the old one. */
    private static final Map<String, Double> SPEED = new LinkedHashMap<>();

    static {
        SPEED.put("QC01", 1.00);
        SPEED.put("QC02", 0.85);
        SPEED.put("QC03", 1.05);
        SPEED.put("QC04", 0.95);
    }

    public record CraneStatus(String craneId, double speedFactor, String heldBy, boolean brokenDown) {}

    private final OpsProperties.Sim sim;
    private final Map<String, String> heldBy = new LinkedHashMap<>();
    private final Set<String> brokenDown = new HashSet<>();

    public CraneSystem(OpsProperties properties) {
        this.sim = properties.sim();
    }

    public List<String> reserve(String visitId, int count) {
        answerSlowly();
        failSometimes("crane reservation system");
        synchronized (this) {
            List<String> held = cranesOf(visitId);
            if (!held.isEmpty()) {
                return held;
            }
            List<String> free = SPEED.keySet().stream().filter(c -> !heldBy.containsKey(c)).limit(count).toList();
            if (free.isEmpty()) {
                // An ordinary exception is retried, so the visit waits until another ship frees a crane.
                throw new IllegalStateException("No quay crane is free for " + visitId);
            }
            free.forEach(c -> heldBy.put(c, visitId));
            return free;
        }
    }

    public void release(String visitId) {
        answerSlowly();
        synchronized (this) {
            for (String crane : cranesOf(visitId)) {
                heldBy.remove(crane);
                brokenDown.remove(crane); // mended before its next ship
            }
        }
    }

    /** Opening a session with the crane's control system; fails now and then. */
    public void connect(String craneId) {
        failSometimes("control system of " + craneId);
    }

    /** Real time per lift: the planning rate times the crane's speed, compressed by the time scale. */
    public Duration timePerMove(String craneId, int movesPerHour, double timeScale) {
        double movesPerRealHour = movesPerHour * SPEED.getOrDefault(craneId, 1.0) * timeScale;
        return Duration.ofMillis(Math.round(3_600_000 / movesPerRealHour));
    }

    /** The move at which the crane breaks down in this attempt, or -1 if it does not. */
    public int breakdownPoint(int fromMove, int moves) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (moves <= fromMove || random.nextDouble() >= sim.breakdownRate()) {
            return -1;
        }
        return fromMove + random.nextInt(moves - fromMove);
    }

    public synchronized void breakDown(String craneId) {
        brokenDown.add(craneId);
    }

    public synchronized List<CraneStatus> status() {
        List<CraneStatus> status = new ArrayList<>();
        SPEED.forEach((crane, speed) ->
                status.add(new CraneStatus(crane, speed, heldBy.get(crane), brokenDown.contains(crane))));
        return status;
    }

    private List<String> cranesOf(String visitId) {
        return heldBy.entrySet().stream().filter(e -> e.getValue().equals(visitId)).map(Map.Entry::getKey).toList();
    }

    private void answerSlowly() {
        try {
            Thread.sleep(sim.latency());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void failSometimes(String what) {
        if (ThreadLocalRandom.current().nextDouble() < sim.failureRate()) {
            throw new IllegalStateException("Lost the link to the " + what + " (simulated)");
        }
    }
}
