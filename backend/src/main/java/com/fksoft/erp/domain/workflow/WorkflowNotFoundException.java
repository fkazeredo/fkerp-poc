package com.fksoft.erp.domain.workflow;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a referenced workflow definition / state / attention rule does not exist. */
public class WorkflowNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorkflowNotFoundException() {
        super("workflow.not-found");
    }
}
