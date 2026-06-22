package com.fksoft.erp.domain.workflow;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a requested transition is not defined in the workflow graph from the record's current state
 * (an unknown transition code, or one whose {@code fromState} is not the current state). This is the
 * data-driven replacement for the per-aggregate transition exceptions (e.g. the old strict-funnel guard).
 */
public class WorkflowTransitionNotAllowedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorkflowTransitionNotAllowedException() {
        super("workflow.transition-not-allowed");
    }
}
