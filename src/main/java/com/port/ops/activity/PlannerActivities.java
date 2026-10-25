package com.port.ops.activity;

import io.temporal.activity.ActivityInterface;

import java.time.Instant;

/** What the visit tells the berth planner. Times are on the terminal clock. */
@ActivityInterface
public interface PlannerActivities {

    /** The ship will finish later than planned: the planner can slow the next ships down. */
    void reportOverrun(String visitId, String berthCode, Instant plannedCompletion, Instant estimatedCompletion);

    /** Cargo work is done and took {@code handlingMinutes}: the planner can refine its handling-time estimates. */
    void reportCompletion(String visitId, String berthCode, Instant cargoOpsStarted, Instant cargoOpsCompleted,
                          long handlingMinutes);

    /** The ship has sailed; the berth is free. */
    void releaseBerth(String visitId, String berthCode, Instant departed);
}
