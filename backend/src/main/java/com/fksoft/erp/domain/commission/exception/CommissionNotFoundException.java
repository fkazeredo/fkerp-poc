package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commission does not exist. */
public class CommissionNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionNotFoundException() {
        super("commission.not-found");
    }
}
