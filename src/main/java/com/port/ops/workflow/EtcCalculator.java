package com.port.ops.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Estimated time of completion (ETC) of cargo operations, from how fast the
 * cranes have actually worked so far.
 *
 * Pure arithmetic with the time passed in, so it is deterministic and is
 * tested without Temporal.
 */
final class EtcCalculator {

    /** One working crane: moves done so far and moves still in its queue. */
    record CraneWork(int movesDone, int movesRemaining) {}

    private EtcCalculator() {}

    /**
     * Each crane finishes after its remaining moves at its observed rate
     * (moves done / hours worked), or at the planning rate until it has
     * finished a bay. The ship is done when the last crane is.
     */
    static Instant estimate(Instant now, double hoursWorked, int planningMovesPerHour, List<CraneWork> cranes) {
        double hoursLeft = 0;
        for (CraneWork c : cranes) {
            if (c.movesRemaining() == 0) {
                continue;
            }
            double rate = c.movesDone() > 0 && hoursWorked > 0 ? c.movesDone() / hoursWorked : planningMovesPerHour;
            hoursLeft = Math.max(hoursLeft, c.movesRemaining() / rate);
        }
        return now.plus(Duration.ofSeconds(Math.round(hoursLeft * 3600)));
    }
}
