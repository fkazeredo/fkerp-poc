package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when reversing a payment that has already been reversed (only a registered payment can be reversed). */
public class PaymentAlreadyReversedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentAlreadyReversedException() {
        super("financial.payment.already-reversed");
    }
}
