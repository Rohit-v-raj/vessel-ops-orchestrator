package com.port.ops.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the berth planner: keeps every message so you can see what the
 * planner would have been told ({@code GET /api/planner/messages}). In memory
 * only, so the list starts empty after a restart.
 */
@Component
public class StubBerthPlannerClient implements BerthPlannerClient {

    private static final Logger log = LoggerFactory.getLogger(StubBerthPlannerClient.class);

    public record PlannerMessage(Instant receivedAt, String visitId, String kind, String text) {}

    private final List<PlannerMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void reportOverrun(String visitId, String berthCode, Instant plannedCompletion,
                              Instant estimatedCompletion) {
        record(visitId, "ETC_OVERRUN", "Cargo work at " + berthCode + " expected to finish at "
                + estimatedCompletion + ", planned " + plannedCompletion
                + ". Replan the next ships and slow them down.");
    }

    @Override
    public void reportCompletion(String visitId, String berthCode, Instant cargoOpsStarted,
                                 Instant cargoOpsCompleted, long handlingMinutes) {
        record(visitId, "CARGO_OPS_COMPLETED", "Cargo work at " + berthCode + " from " + cargoOpsStarted
                + " to " + cargoOpsCompleted + ": " + handlingMinutes + " minutes of handling.");
    }

    @Override
    public void releaseBerth(String visitId, String berthCode, Instant departed) {
        record(visitId, "BERTH_RELEASED", "Ship left " + berthCode + " at " + departed + ".");
    }

    public List<PlannerMessage> messages() {
        return List.copyOf(messages);
    }

    private void record(String visitId, String kind, String text) {
        log.info("To the berth planner [{}] {}: {}", kind, visitId, text);
        messages.add(new PlannerMessage(Instant.now(), visitId, kind, text));
    }
}
