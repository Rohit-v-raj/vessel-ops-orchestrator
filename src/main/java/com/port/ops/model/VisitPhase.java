package com.port.ops.model;

public enum VisitPhase {
    /** Berth confirmed; waiting for the shipping line's move list. */
    AWAITING_MOVE_LIST,
    /** Move list in hand; waiting for the ship to be alongside. */
    AWAITING_BERTHING,
    /** Cranes are working the ship. */
    WORKING,
    /** Cargo work done; lashing and paperwork before the ship sails. */
    COMPLETED,
    /** The ship has left and the berth is released. */
    DEPARTED,
    FAILED
}
