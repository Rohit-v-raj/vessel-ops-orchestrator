package com.port.ops.workflow;

import com.port.ops.workflow.EtcCalculator.CraneWork;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EtcCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-25T08:00:00Z");

    @Test
    void beforeAnyBayIsDoneThePlanningRateIsUsed() {
        Instant etc = EtcCalculator.estimate(NOW, 0, 30, List.of(new CraneWork(0, 300), new CraneWork(0, 150)));

        assertThat(etc).isEqualTo(NOW.plus(Duration.ofHours(10)));
    }

    @Test
    void theSlowestCraneDecides() {
        // After 4 hours: one crane did 120 moves (30/h), the other 80 (20/h).
        Instant etc = EtcCalculator.estimate(NOW, 4, 30, List.of(new CraneWork(120, 60), new CraneWork(80, 60)));

        assertThat(etc).isEqualTo(NOW.plus(Duration.ofHours(3)));
    }

    @Test
    void finishedCranesDoNotCount() {
        Instant etc = EtcCalculator.estimate(NOW, 4, 30, List.of(new CraneWork(200, 0)));

        assertThat(etc).isEqualTo(NOW);
    }
}
