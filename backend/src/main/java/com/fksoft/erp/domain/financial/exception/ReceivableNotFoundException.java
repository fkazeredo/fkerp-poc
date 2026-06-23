package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Receivable does not exist. */
public class ReceivableNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReceivableNotFoundException() {
        super("financial.receivable.not-found");
    }
}
