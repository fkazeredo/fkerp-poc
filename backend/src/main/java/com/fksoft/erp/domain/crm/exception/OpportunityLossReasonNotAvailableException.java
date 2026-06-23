package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when marking an Opportunity as lost with an unknown or inactive OpportunityLossReason. */
public class OpportunityLossReasonNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityLossReasonNotAvailableException() {
        super("opportunity.loss-reason-not-available");
    }
}
