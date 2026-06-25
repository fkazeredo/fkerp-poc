package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when no active, in-window Commission Rule applies to the Order's commercial responsible (no user-specific
 * rule and no generic {@code COMMERCIAL_RESPONSIBLE} rule).
 */
public class NoApplicableCommissionRuleException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NoApplicableCommissionRuleException() {
        super("commission.no-applicable-rule");
    }
}
