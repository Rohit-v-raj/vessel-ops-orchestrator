package com.port.ops.model;

import java.time.Instant;

/**
 * One time in the life of the visit, DCSA style: which event it is about and
 * how firm it is. A simplified take on the DCSA Port Call timestamps; full
 * compliance is not a goal.
 */
public record OpsTimestamp(Milestone milestone, Type type, Instant time) {

    public enum Milestone { ARRIVAL_BERTH, CARGO_OPS_START, CARGO_OPS_COMPLETE, DEPARTURE_BERTH }

    /** EST estimated, PLN planned (agreed with the planner), ACT actual. */
    public enum Type { EST, PLN, ACT }
}
