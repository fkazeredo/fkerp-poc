package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when generating a commission from an Order that is not commercially closed (e.g. cancelled). */
public class CommissionOrderNotClosedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionOrderNotClosedException() {
        super("commission.order-not-closed");
    }
}
