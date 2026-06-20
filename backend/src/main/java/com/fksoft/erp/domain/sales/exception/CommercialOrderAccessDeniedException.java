package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the caller is not allowed to see a Commercial Order. */
public class CommercialOrderAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommercialOrderAccessDeniedException() {
        super("order.access-denied");
    }
}
