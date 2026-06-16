package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Lead cannot be qualified from its current status (already qualified or lost). */
public class LeadCannotBeQualifiedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadCannotBeQualifiedException() {
        super("lead.cannot-qualify");
    }
}
