package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commission Rule's percentage is not in the valid range (greater than zero and at most 100). */
public class CommissionRulePercentageInvalidException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionRulePercentageInvalidException() {
        super("commission.rule.percentage-invalid");
    }
}
