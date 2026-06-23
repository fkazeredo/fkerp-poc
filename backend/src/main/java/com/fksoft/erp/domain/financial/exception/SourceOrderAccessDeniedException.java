package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the caller may not see the source Commercial Order of the Receivable they want to create. */
public class SourceOrderAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SourceOrderAccessDeniedException() {
        super("financial.receivable.order-access-denied");
    }
}
