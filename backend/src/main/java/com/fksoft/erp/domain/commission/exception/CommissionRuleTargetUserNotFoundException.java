package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commission Rule targets a specific user that does not exist or is inactive. */
public class CommissionRuleTargetUserNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionRuleTargetUserNotFoundException() {
        super("commission.rule.target-user-not-found");
    }
}
