package com.fksoft.erp.domain.commission.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The defining attributes of a {@link CommissionRule} (a value object used to create/update one) — without the
 * soft safe-limit override, which is a service concern.
 *
 * @param name the rule name
 * @param percentage the commission percentage of the received amount
 * @param targetType the commercial actor the rule targets
 * @param targetUserId the specific user the rule targets, or {@code null} for all actors of the type
 * @param startDate the validity start date
 * @param endDate the validity end date, or {@code null}
 * @param notes optional free-text notes
 */
public record CommissionRuleData(
        String name,
        BigDecimal percentage,
        CommissionTargetType targetType,
        UUID targetUserId,
        LocalDate startDate,
        LocalDate endDate,
        String notes) {}
