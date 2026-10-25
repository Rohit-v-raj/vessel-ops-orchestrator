package com.port.ops.model;

/**
 * The workflow's input: the berth the planner confirmed, plus the settings in
 * force when it was confirmed.
 *
 * The settings travel in the input, not in Spring configuration, because a
 * workflow must make the same decisions every time it is replayed. If it read
 * {@code application.yml} and someone changed the file, a replay after a
 * restart would take a different path from the recorded history.
 */
public record VisitStart(
        String visitId,
        String imo,
        String voyageNo,
        String vesselName,
        String berthCode,
        /** How long the planner has given the ship at the berth. */
        double plannedBerthHours,
        VisitSettings settings) {
}
