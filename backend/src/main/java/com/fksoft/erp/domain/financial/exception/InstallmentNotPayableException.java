package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering a payment against an installment that is already paid or cancelled. */
public class InstallmentNotPayableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InstallmentNotPayableException() {
        super("financial.payment.installment-not-payable");
    }
}
