package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering a payment for a Commission that is not Approved (only an Approved commission can be paid). */
public class CommissionNotPayableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionNotPayableException() {
        super("commission.not-payable");
    }
}
