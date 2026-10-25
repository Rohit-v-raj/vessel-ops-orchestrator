package com.port.ops.activity;

import io.temporal.activity.ActivityInterface;

import java.util.List;

/**
 * Everything the visit asks of the quay cranes. Each method is one activity:
 * it can fail and be retried, and its result is recorded in history.
 */
@ActivityInterface
public interface CraneActivities {

    /**
     * Reserves up to {@code count} free cranes for the visit and returns them,
     * in rail order. Idempotent: a retry gets the cranes already held.
     */
    List<String> reserveCranes(String visitId, int count);

    /**
     * Lifts {@code moves} containers in one bay. Heartbeats after every move,
     * so a retry resumes where the last attempt stopped. A breakdown fails
     * with type {@code CraneBreakdown}, non-retryable, carrying the moves done.
     */
    void workBay(String visitId, String craneId, int bay, int moves);

    /** Frees every crane the visit holds. Idempotent. */
    void releaseCranes(String visitId);
}
