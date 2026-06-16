package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Lead with the given id does not exist. */
public class LeadNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadNotFoundException() {
        super("lead.not-found");
    }
}
