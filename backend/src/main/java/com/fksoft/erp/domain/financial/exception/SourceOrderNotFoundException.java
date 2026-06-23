package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the source Commercial Order of a Receivable does not exist. */
public class SourceOrderNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SourceOrderNotFoundException() {
        super("financial.receivable.order-not-found");
    }
}
