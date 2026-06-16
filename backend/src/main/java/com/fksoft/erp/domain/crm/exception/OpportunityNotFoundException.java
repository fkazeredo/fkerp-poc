package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when an Opportunity does not exist. */
public class OpportunityNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityNotFoundException() {
        super("opportunity.not-found");
    }
}
