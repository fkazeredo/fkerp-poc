package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering a Lead with an unknown or inactive Origin. */
public class OriginNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OriginNotAvailableException() {
        super("lead.origin-not-available");
    }
}
