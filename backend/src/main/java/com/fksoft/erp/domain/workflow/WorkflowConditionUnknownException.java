package com.fksoft.erp.domain.workflow;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when an attention rule references a condition key not in the workflow's catalog. */
public class WorkflowConditionUnknownException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorkflowConditionUnknownException() {
        super("workflow.condition-unknown");
    }
}
