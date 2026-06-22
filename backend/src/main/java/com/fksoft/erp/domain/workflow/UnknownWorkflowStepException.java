package com.fksoft.erp.domain.workflow;

import java.io.Serial;

/**
 * Raised when a {@link WorkflowRule} references a catalog key with no registered {@link WorkflowGuard} or
 * {@link WorkflowPostFunction} bean. This is a configuration/programming error (a seed or an edit referenced
 * a step that does not exist), not a business error, so it is an unchecked runtime failure (surfaces as 500).
 */
public class UnknownWorkflowStepException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnknownWorkflowStepException(String key) {
        super("No workflow step registered for key: " + key);
    }
}
