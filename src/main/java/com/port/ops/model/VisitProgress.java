package com.port.ops.model;

import java.util.List;

/** What the progress query answers: a snapshot of the workflow's own fields. */
public record VisitProgress(
        String visitId,
        String vesselName,
        String berthCode,
        VisitPhase phase,
        /** Null until the move list arrives. */
        Integer totalMoves,
        int movesDone,
        List<CraneProgress> cranes,
        List<OpsTimestamp> timestamps,
        boolean overrunReported,
        /** Why the visit failed; null otherwise. */
        String failure) {

    public record CraneProgress(String craneId, String status, List<Integer> baysLeft,
                                int movesDone, int movesRemaining) {}
}
