package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when the registered commission-payment amount does not equal the approved commission amount (commission
 * payment is full-only — partial commission payment is out of scope in Sprint 6).
 */
public class CommissionPaymentAmountMismatchException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionPaymentAmountMismatchException() {
        super("commission.payment-amount-mismatch");
    }
}
