package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a user tries to access an Opportunity they are not allowed to see. */
public class OpportunityAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityAccessDeniedException() {
        super("opportunity.access-denied");
    }
}
