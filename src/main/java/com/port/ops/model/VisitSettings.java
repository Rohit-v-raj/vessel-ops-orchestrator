package com.port.ops.model;

/** Planning settings, copied from {@code ops.*} when a visit starts. Durations are in terminal minutes. */
public record VisitSettings(
        double timeScale,
        int movesPerHour,
        int movesPerCrane,
        int maxCranes,
        long moveListDeadlineMinutes,
        long overrunThresholdMinutes,
        long departureAfterCompletionMinutes) {
}
