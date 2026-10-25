package com.port.ops.config;

import com.port.ops.model.VisitSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** The {@code ops.*} settings in {@code application.yml}. */
@ConfigurationProperties("ops")
public record OpsProperties(
        double timeScale,
        int movesPerHour,
        int movesPerCrane,
        int maxCranes,
        Duration moveListDeadline,
        Duration overrunThreshold,
        Duration departureAfterCompletion,
        Sim sim) {

    public record Sim(double failureRate, double breakdownRate, Duration latency) {}

    /** The copy a new workflow gets in its input; see {@link com.port.ops.model.VisitStart}. */
    public VisitSettings toVisitSettings() {
        return new VisitSettings(timeScale, movesPerHour, movesPerCrane, maxCranes,
                moveListDeadline.toMinutes(), overrunThreshold.toMinutes(), departureAfterCompletion.toMinutes());
    }
}
