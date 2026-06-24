package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering a payment with an unknown or inactive payment method. */
public class PaymentMethodNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentMethodNotAvailableException() {
        super("financial.payment.method-not-available");
    }
}
