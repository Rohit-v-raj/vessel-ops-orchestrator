package com.port.ops.activity;

import com.port.ops.TaskQueues;
import com.port.ops.planner.BerthPlannerClient;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The activity is the Temporal boundary (timeouts, retries, history); the
 * client behind it is the integration boundary. Today the client is a stub.
 * Connecting the real berth planner means a new client, not a new activity.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.VESSEL_VISITS)
public class PlannerActivitiesImpl implements PlannerActivities {

    private final BerthPlannerClient planner;

    public PlannerActivitiesImpl(BerthPlannerClient planner) {
        this.planner = planner;
    }

    @Override
    public void reportOverrun(String visitId, String berthCode, Instant plannedCompletion,
                              Instant estimatedCompletion) {
        planner.reportOverrun(visitId, berthCode, plannedCompletion, estimatedCompletion);
    }

    @Override
    public void reportCompletion(String visitId, String berthCode, Instant cargoOpsStarted,
                                 Instant cargoOpsCompleted, long handlingMinutes) {
        planner.reportCompletion(visitId, berthCode, cargoOpsStarted, cargoOpsCompleted, handlingMinutes);
    }

    @Override
    public void releaseBerth(String visitId, String berthCode, Instant departed) {
        planner.releaseBerth(visitId, berthCode, departed);
    }
}
