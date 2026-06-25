package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when generating a commission from an Order that has no positive commercial amount. */
public class CommissionOrderNoAmountException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionOrderNoAmountException() {
        super("commission.order-no-amount");
    }
}
