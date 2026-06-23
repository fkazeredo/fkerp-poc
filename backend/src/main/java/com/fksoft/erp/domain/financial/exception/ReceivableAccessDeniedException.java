package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Receivable exists but is not visible to the caller. */
public class ReceivableAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReceivableAccessDeniedException() {
        super("financial.receivable.access-denied");
    }
}
