package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the payment to reverse does not belong to the Receivable. */
public class PaymentNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException() {
        super("financial.payment.not-found");
    }
}
