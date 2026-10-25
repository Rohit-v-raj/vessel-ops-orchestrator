package com.port.ops.api;

import com.port.ops.TaskQueues;
import com.port.ops.config.OpsProperties;
import com.port.ops.model.MoveList;
import com.port.ops.model.MoveListAccepted;
import com.port.ops.model.VisitProgress;
import com.port.ops.model.VisitStart;
import com.port.ops.workflow.VesselVisitWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.failure.ApplicationFailure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The REST face of the visit workflow. This class is a Temporal <b>client</b>:
 * it starts workflows and sends them messages, but runs none of their code.
 * The workers do, and they may be in another process.
 */
@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final WorkflowClient client;
    private final OpsProperties properties;

    public VisitController(WorkflowClient client, OpsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** What the berth planner sends when it confirms a berth. */
    public record BerthConfirmed(
            @Pattern(regexp = "\\d{7}", message = "must be a 7-digit IMO number") String imo,
            @Pattern(regexp = "[A-Za-z0-9]{1,12}", message = "must be 1 to 12 letters or digits") String voyageNo,
            @NotBlank String vesselName,
            @NotBlank String berthCode,
            @Positive double plannedBerthHours) {}

    /**
     * Starts the visit's workflow. The workflow ID is the business key, so a
     * planner that sends the same confirmation twice (a retry after a timeout,
     * say) gets the visit already running rather than a second one.
     */
    @PostMapping
    public Map<String, String> confirmBerth(@Valid @RequestBody BerthConfirmed request) {
        String visitId = "visit-" + request.imo() + "-" + request.voyageNo();
        VesselVisitWorkflow workflow = client.newWorkflowStub(VesselVisitWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(visitId)
                        .setTaskQueue(TaskQueues.VESSEL_VISITS)
                        .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                        .build());
        VisitStart start = new VisitStart(visitId, request.imo(), request.voyageNo(), request.vesselName(),
                request.berthCode(), request.plannedBerthHours(), properties.toVisitSettings());
        // Returns as soon as the server has recorded the start; the visit runs on without us.
        WorkflowExecution execution = WorkflowClient.start(workflow::run, start);
        return Map.of("workflowId", execution.getWorkflowId(), "runId", execution.getRunId());
    }

    /** An update: waits for the workflow's answer, or is refused by its validator. */
    @PutMapping("/{visitId}/move-list")
    public MoveListAccepted submitMoveList(@PathVariable String visitId, @RequestBody MoveList moveList) {
        return visit(visitId).submitMoveList(moveList);
    }

    /** A signal: returns once the server has recorded it, without waiting for the workflow. */
    @PostMapping("/{visitId}/berthed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> vesselBerthed(@PathVariable String visitId) {
        visit(visitId).vesselBerthed();
        return Map.of("visitId", visitId, "signal", "vesselBerthed");
    }

    /** A query: answered by a worker from the workflow's current state, even after it has finished. */
    @GetMapping("/{visitId}")
    public VisitProgress progress(@PathVariable String visitId) {
        return visit(visitId).progress();
    }

    private VesselVisitWorkflow visit(String visitId) {
        return client.newWorkflowStub(VesselVisitWorkflow.class, visitId);
    }

    @ExceptionHandler(WorkflowUpdateException.class)
    public ResponseEntity<Map<String, String>> rejected(WorkflowUpdateException e) {
        String reason = e.getCause() instanceof ApplicationFailure failure
                ? failure.getOriginalMessage() : e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("rejected", reason));
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(WorkflowNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No running visit " + e.getExecution().getWorkflowId()));
    }
}
