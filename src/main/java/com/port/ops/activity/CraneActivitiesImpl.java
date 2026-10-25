package com.port.ops.activity;

import com.port.ops.TaskQueues;
import com.port.ops.config.OpsProperties;
import com.port.ops.sim.CraneSystem;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The activities are ordinary Spring beans: unlike workflow code they may
 * read configuration, sleep, call other systems and fail at random. Temporal
 * records only what they return or throw.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.QUAY_CRANES)
public class CraneActivitiesImpl implements CraneActivities {

    private static final Logger log = LoggerFactory.getLogger(CraneActivitiesImpl.class);

    private final CraneSystem craneSystem;
    private final OpsProperties properties;

    public CraneActivitiesImpl(CraneSystem craneSystem, OpsProperties properties) {
        this.craneSystem = craneSystem;
        this.properties = properties;
    }

    @Override
    public List<String> reserveCranes(String visitId, int count) {
        return craneSystem.reserve(visitId, count);
    }

    @Override
    public void workBay(String visitId, String craneId, int bay, int moves) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        // The last heartbeat of the previous attempt, if there was one: after a
        // worker crash the moves already lifted are not lifted again.
        int done = context.getHeartbeatDetails(Integer.class).orElse(0);
        if (done > 0) {
            log.info("{} resumes bay {} at move {} of {} (attempt {})",
                    craneId, bay, done + 1, moves, context.getInfo().getAttempt());
        }
        craneSystem.connect(craneId);

        Duration perMove = craneSystem.timePerMove(craneId, properties.movesPerHour(), properties.timeScale());
        int breaksAt = craneSystem.breakdownPoint(done, moves);
        for (int move = done; move < moves; move++) {
            if (move == breaksAt) {
                craneSystem.breakDown(craneId);
                // Non-retryable: trying again will not mend the crane. The detail
                // tells the workflow how much of the bay is left for another crane.
                throw ApplicationFailure.newNonRetryableFailure(
                        craneId + " broke down in bay " + bay + " after " + move + " of " + moves + " moves",
                        "CraneBreakdown", move);
            }
            sleep(perMove);
            context.heartbeat(move + 1);
        }
    }

    @Override
    public void releaseCranes(String visitId) {
        craneSystem.release(visitId);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while lifting", e);
        }
    }
}
