package com.port.ops.workflow;

import com.port.ops.model.MoveList;
import com.port.ops.model.MoveListAccepted;
import com.port.ops.model.VisitProgress;
import com.port.ops.model.VisitResult;
import com.port.ops.model.VisitStart;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One vessel visit, from the berth being confirmed to the ship sailing.
 * Workflow ID {@code visit-{imo}-{voyageNo}}.
 *
 * The four ways to talk to a running workflow, one of each:
 * <ul>
 *   <li><b>workflow method</b>: starts it and, at the end, returns its result</li>
 *   <li><b>update</b>: the move list. The caller needs to know at once whether it
 *       was accepted, and a bad list must be turned away without a trace in
 *       history, so it is validated first</li>
 *   <li><b>signal</b>: the ship is alongside. A fact to record; nothing to
 *       answer and nothing to refuse</li>
 *   <li><b>query</b>: progress and estimated completion. Read-only, not recorded</li>
 * </ul>
 */
@WorkflowInterface
public interface VesselVisitWorkflow {

    @WorkflowMethod
    VisitResult run(VisitStart start);

    @UpdateMethod
    MoveListAccepted submitMoveList(MoveList moveList);

    /** Runs before the update is accepted. Throwing rejects it, and nothing is written to history. */
    @UpdateValidatorMethod(updateName = "submitMoveList")
    void validateMoveList(MoveList moveList);

    @SignalMethod
    void vesselBerthed();

    @QueryMethod
    VisitProgress progress();
}
