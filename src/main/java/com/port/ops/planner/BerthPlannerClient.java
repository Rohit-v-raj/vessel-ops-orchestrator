package com.port.ops.planner;

import java.time.Instant;

/**
 * Every call to the berth planner. The only implementation today is
 * {@link StubBerthPlannerClient}; an HTTP client for the real planner
 * (ship-eta-berth-scheduler) is in the backlog.
 */
public interface BerthPlannerClient {

    void reportOverrun(String visitId, String berthCode, Instant plannedCompletion, Instant estimatedCompletion);

    void reportCompletion(String visitId, String berthCode, Instant cargoOpsStarted, Instant cargoOpsCompleted,
                          long handlingMinutes);

    void releaseBerth(String visitId, String berthCode, Instant departed);
}
