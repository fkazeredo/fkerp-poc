package com.fksoft.erp.domain.workflow;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when attempting to delete a {@code system} attention rule (its code/condition are protected). */
public class WorkflowSystemRuleProtectedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WorkflowSystemRuleProtectedException() {
        super("workflow.rule-system-protected");
    }
}
