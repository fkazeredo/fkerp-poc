package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when an Opportunity cannot be marked as won from its current stage (already closed: won or lost). */
public class OpportunityCannotBeMarkedWonException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityCannotBeMarkedWonException() {
        super("opportunity.cannot-mark-won");
    }
}
