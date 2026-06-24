package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a payment's amount exceeds the installment's outstanding amount (overpayment is out of scope; a
 * payment may settle the installment fully or partially, but never beyond its balance).
 */
public class PaymentExceedsOutstandingException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentExceedsOutstandingException() {
        super("financial.payment.exceeds-outstanding");
    }
}
