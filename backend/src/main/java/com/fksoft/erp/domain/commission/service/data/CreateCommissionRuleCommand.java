package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to create (or update) a Commission Rule.
 *
 * @param name the rule name (required)
 * @param percentage the commission percentage of the received amount (greater than zero, at most 100)
 * @param targetType the commercial actor the rule targets
 * @param targetUserId the specific user the rule targets, or {@code null} for all actors of the type
 * @param startDate the validity start date (required)
 * @param endDate the validity end date, or {@code null}
 * @param notes optional free-text notes
 * @param allowAboveLimit whether the caller explicitly allows a percentage above the configured safe limit
 */
public record CreateCommissionRuleCommand(
        String name,
        BigDecimal percentage,
        CommissionTargetType targetType,
        UUID targetUserId,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        boolean allowAboveLimit) {}
