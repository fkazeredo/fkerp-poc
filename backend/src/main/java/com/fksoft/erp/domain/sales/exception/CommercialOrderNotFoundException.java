package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commercial Order does not exist. */
public class CommercialOrderNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommercialOrderNotFoundException() {
        super("order.not-found");
    }
}
