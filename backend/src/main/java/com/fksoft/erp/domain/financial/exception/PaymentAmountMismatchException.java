package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a full payment's amount does not equal the installment's amount (this slice registers full
 * payments only; partial payments are a later slice).
 */
public class PaymentAmountMismatchException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentAmountMismatchException() {
        super("financial.payment.amount-mismatch");
    }
}
