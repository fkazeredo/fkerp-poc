package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;
import java.math.BigDecimal;

/**
 * Raised when a Commission Rule's percentage exceeds the configured safe business limit and the create/update was
 * not explicitly allowed to exceed it ({@code allowAboveLimit}). The configured limit is the message argument.
 */
public class CommissionRulePercentageAboveLimitException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionRulePercentageAboveLimitException(BigDecimal limit) {
        super("commission.rule.percentage-above-limit", limit.toPlainString());
    }
}
