package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering an activity with an unknown or inactive OpportunityActivityType. */
public class OpportunityActivityTypeNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityActivityTypeNotAvailableException() {
        super("opportunity.activity-type-not-available");
    }
}
