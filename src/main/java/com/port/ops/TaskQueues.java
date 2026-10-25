package com.port.ops;

/**
 * The two task queues. A task queue is how work reaches a worker: the server
 * puts tasks on a queue and any worker polling that queue may take them.
 *
 * Crane work has its own queue so that it could run on other machines (next to
 * the cranes, say) without changing the workflow: only the worker that polls
 * {@link #QUAY_CRANES} would move.
 */
public final class TaskQueues {

    /** The visit workflow and the calls to the berth planner. */
    public static final String VESSEL_VISITS = "vessel-visits";

    /** The activities that drive the quay cranes. */
    public static final String QUAY_CRANES = "quay-cranes";

    private TaskQueues() {}
}
