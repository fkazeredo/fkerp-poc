package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the caller may not see a Commission (own-tier reader viewing another beneficiary's commission). */
public class CommissionAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionAccessDeniedException() {
        super("commission.access-denied");
    }
}
