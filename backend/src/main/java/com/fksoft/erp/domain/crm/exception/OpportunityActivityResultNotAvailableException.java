package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering an activity with an unknown or inactive OpportunityActivityResult. */
public class OpportunityActivityResultNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityActivityResultNotAvailableException() {
        super("opportunity.activity-result-not-available");
    }
}
