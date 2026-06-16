package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when an Opportunity stage transition is not allowed: moving from LOST (terminal), moving to LOST
 * through the stage endpoint (use the lose action), or moving to the stage it is already in.
 */
public class OpportunityStageTransitionException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityStageTransitionException() {
        super("opportunity.invalid-stage-transition");
    }
}
