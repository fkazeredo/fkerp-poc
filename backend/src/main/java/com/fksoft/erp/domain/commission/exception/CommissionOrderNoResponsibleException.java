package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when generating a commission from an Order that has no commercial responsible (no beneficiary). */
public class CommissionOrderNoResponsibleException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionOrderNoResponsibleException() {
        super("commission.order-no-responsible");
    }
}
