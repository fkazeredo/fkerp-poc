package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.CommissionRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of a Commission Rule in the management list. Carries rule data only — never Commission or Payment data.
 *
 * @param id the rule id
 * @param name the rule name
 * @param percentage the commission percentage of the received amount
 * @param targetType the commercial actor the rule targets
 * @param targetUserId the specific user the rule targets, or {@code null}
 * @param targetUserName the resolved name of the target user, or {@code null}
 * @param active whether the rule is active
 * @param startDate the validity start date
 * @param endDate the validity end date, or {@code null}
 */
public record CommissionRuleListItem(
        UUID id,
        String name,
        BigDecimal percentage,
        String targetType,
        UUID targetUserId,
        String targetUserName,
        boolean active,
        LocalDate startDate,
        LocalDate endDate) {

    /**
     * Builds the list item from the entity and the resolved target user name.
     *
     * @param rule the rule entity
     * @param targetUserName the resolved target user name, or {@code null}
     * @return the read view
     */
    public static CommissionRuleListItem from(CommissionRule rule, String targetUserName) {
        return new CommissionRuleListItem(
                rule.id(),
                rule.name(),
                rule.percentage(),
                rule.targetType().name(),
                rule.targetUserId(),
                targetUserName,
                rule.active(),
                rule.startDate(),
                rule.endDate());
    }
}
