package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the Commercial Order a commission is being generated from does not exist. */
public class CommissionSourceOrderNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionSourceOrderNotFoundException() {
        super("commission.order-not-found");
    }
}
