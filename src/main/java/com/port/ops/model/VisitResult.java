package com.port.ops.model;

import java.util.List;

/** What the workflow returns when the ship has left. */
public record VisitResult(
        String visitId,
        int totalMoves,
        List<String> cranesUsed,
        List<String> cranesBrokenDown,
        /** Terminal minutes from the first move to the last. */
        long handlingMinutes,
        boolean overrunReported) {
}
