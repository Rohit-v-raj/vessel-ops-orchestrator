package com.port.ops.workflow;

import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.Instant;

/**
 * The compressed clock that lets a 12-hour visit run in two minutes.
 *
 * Terminal time starts at the real moment the workflow starts and then runs
 * {@code scale} times faster. Every wait in the workflow is a terminal duration
 * turned into a real one here, and every time the workflow reports is a
 * terminal instant.
 *
 * It reads the time only through {@link Workflow#currentTimeMillis()}, which
 * gives the time recorded in history. On replay it gives the same answer,
 * where {@code Instant.now()} would not.
 */
final class TerminalClock {

    private final long startRealMillis;
    private final double scale;

    TerminalClock(long startRealMillis, double scale) {
        this.startRealMillis = startRealMillis;
        this.scale = scale;
    }

    /** Only from workflow code. */
    static TerminalClock startingNow(double scale) {
        return new TerminalClock(Workflow.currentTimeMillis(), scale);
    }

    /** How long to really wait for {@code terminal} to pass. At least 1 ms: a zero timer is no timer. */
    Duration real(Duration terminal) {
        return Duration.ofMillis(Math.max(1, Math.round(terminal.toMillis() / scale)));
    }

    /** The terminal instant of a real one. */
    Instant at(long realMillis) {
        return Instant.ofEpochMilli(startRealMillis + Math.round((realMillis - startRealMillis) * scale));
    }

    /** Only from workflow code. */
    Instant now() {
        return at(Workflow.currentTimeMillis());
    }
}
