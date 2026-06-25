package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the caller may not see the Commercial Order a commission is being generated from. */
public class CommissionSourceOrderAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionSourceOrderAccessDeniedException() {
        super("commission.order-access-denied");
    }
}
