package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when rejecting/cancelling a Commission with an unknown or inactive resolution reason. */
public class CommissionResolutionReasonNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionResolutionReasonNotAvailableException() {
        super("commission.resolution-reason-not-available");
    }
}
