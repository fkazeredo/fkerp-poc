package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when an Opportunity cannot be marked as lost from its current stage (already lost). */
public class OpportunityCannotBeMarkedLostException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityCannotBeMarkedLostException() {
        super("opportunity.cannot-mark-lost");
    }
}
