package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commission Rule does not exist. */
public class CommissionRuleNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionRuleNotFoundException() {
        super("commission.rule.not-found");
    }
}
