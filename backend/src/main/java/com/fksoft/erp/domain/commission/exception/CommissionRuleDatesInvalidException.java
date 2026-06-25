package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commission Rule's end date is before its start date. */
public class CommissionRuleDatesInvalidException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionRuleDatesInvalidException() {
        super("commission.rule.dates-invalid");
    }
}
