package com.port.ops.model;

/** The workflow's answer to a move list it accepted. */
public record MoveListAccepted(int bays, int totalMoves, int plannedCranes) {
}
