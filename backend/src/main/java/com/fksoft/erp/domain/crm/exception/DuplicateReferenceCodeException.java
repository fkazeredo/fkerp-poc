package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when creating a reference value with a code that already exists. */
public class DuplicateReferenceCodeException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateReferenceCodeException(String code) {
        super("reference.duplicate-code", code);
    }
}
