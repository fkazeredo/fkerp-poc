package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the target installment of a payment does not belong to the Receivable. */
public class PaymentInstallmentNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentInstallmentNotFoundException() {
        super("financial.payment.installment-not-found");
    }
}
