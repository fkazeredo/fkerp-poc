package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when an Opportunity is requested from a Lead that is not QUALIFIED. */
public class LeadNotQualifiedForOpportunityException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadNotQualifiedForOpportunityException() {
        super("opportunity.lead-not-qualified");
    }
}
