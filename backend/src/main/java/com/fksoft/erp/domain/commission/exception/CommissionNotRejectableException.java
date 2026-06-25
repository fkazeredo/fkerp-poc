package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when rejecting a Commission that is not Eligible (only an Eligible commission can be rejected). */
public class CommissionNotRejectableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionNotRejectableException() {
        super("commission.not-rejectable");
    }
}
