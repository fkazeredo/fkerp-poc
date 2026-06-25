package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.CommissionRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of a Commission Rule (configuration). Carries rule data only — never Commission, Payment, payroll,
 * payable, tax or accounting data.
 *
 * @param id the rule id
 * @param name the rule name
 * @param percentage the commission percentage of the received amount
 * @param targetType the commercial actor the rule targets
 * @param targetUserId the specific user the rule targets, or {@code null}
 * @param targetUserName the resolved name of the target user, or {@code null}
 * @param active whether the rule is active (usable for new commission calculation)
 * @param startDate the validity start date
 * @param endDate the validity end date, or {@code null}
 * @param notes optional notes
 * @param createdAt when the rule was created
 */
public record CommissionRuleDetail(
        UUID id,
        String name,
        BigDecimal percentage,
        String targetType,
        UUID targetUserId,
        String targetUserName,
        boolean active,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        Instant createdAt) {

    /**
     * Builds the detail from the entity and the resolved target user name.
     *
     * @param rule the rule entity
     * @param targetUserName the resolved target user name, or {@code null}
     * @return the read view
     */
    public static CommissionRuleDetail from(CommissionRule rule, String targetUserName) {
        return new CommissionRuleDetail(
                rule.id(),
                rule.name(),
                rule.percentage(),
                rule.targetType().name(),
                rule.targetUserId(),
                targetUserName,
                rule.active(),
                rule.startDate(),
                rule.endDate(),
                rule.notes(),
                rule.createdAt());
    }
}
