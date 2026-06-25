package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when approving a Commission that is not Eligible (only an Eligible commission can be approved). */
public class CommissionNotEligibleException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionNotEligibleException() {
        super("commission.not-eligible");
    }
}
