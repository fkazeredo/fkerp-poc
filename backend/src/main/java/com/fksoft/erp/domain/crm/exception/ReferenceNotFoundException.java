package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a reference-data value is not found by id. */
public class ReferenceNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReferenceNotFoundException() {
        super("reference.not-found");
    }
}
