package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivity;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReasons;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Opportunities that need action — the OR of the active attention rules' conditions
 * (excluding the terminal WON / LOST stages). Mirrors {@link OpportunityPendingReasons#of} so the page
 * contains exactly the Opportunities that have at least one reason; both are driven by the same configurable
 * {@link WorkflowAttentionRule}s.
 */
public final class OpportunityPendingSpecifications {

    private OpportunityPendingSpecifications() {}

    /**
     * An Opportunity is pending (and not WON / LOST) when at least one active attention rule matches.
     *
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param rules the active attention rules of the {@code opportunity} workflow
     * @return the pending Specification
     */
    public static Specification<Opportunity> pending(Instant now, LocalDate today, List<WorkflowAttentionRule> rules) {
        return (root, query, cb) -> {
            List<Predicate> ors = new ArrayList<>();
            for (WorkflowAttentionRule rule : rules) {
                Predicate predicate = predicate(rule, root, query, cb, now, today);
                if (predicate != null) {
                    ors.add(predicate);
                }
            }
            Predicate any = ors.isEmpty() ? cb.disjunction() : cb.or(ors.toArray(Predicate[]::new));
            return cb.and(cb.not(root.get("stage").in(List.of("WON", "LOST"))), any);
        };
    }

    private static Predicate predicate(
            WorkflowAttentionRule rule,
            Root<Opportunity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Instant now,
            LocalDate today) {
        return switch (rule.conditionKey()) {
            case "NO_RECENT_ACTIVITY" -> {
                Instant staleBefore = now.minus(days(rule), ChronoUnit.DAYS);
                Subquery<UUID> recentActivity = query.subquery(UUID.class);
                var a = recentActivity.from(OpportunityActivity.class);
                recentActivity
                        .select(a.get("opportunityId"))
                        .where(
                                cb.equal(a.get("opportunityId"), root.get("id")),
                                cb.greaterThanOrEqualTo(a.<Instant>get("occurredAt"), staleBefore));
                yield cb.and(
                        cb.lessThan(root.<Instant>get("createdAt"), staleBefore), cb.not(cb.exists(recentActivity)));
            }
            case "NEXT_ACTION_OVERDUE" -> cb.and(
                    cb.isNotNull(root.get("nextActionDate")),
                    cb.lessThan(root.<LocalDate>get("nextActionDate"), today));
            case "IN_STATE_LONGER_THAN" -> cb.and(
                    cb.equal(root.get("stage"), rule.stateValue()),
                    cb.lessThan(root.<Instant>get("createdAt"), now.minus(days(rule), ChronoUnit.DAYS)));
            case "IN_STATE" -> cb.equal(root.get("stage"), rule.stateValue());
            case "EXPECTED_CLOSE_OVERDUE" -> cb.and(
                    cb.isNotNull(root.get("expectedCloseDate")),
                    cb.lessThan(root.<LocalDate>get("expectedCloseDate"), today));
            default -> null;
        };
    }

    private static int days(WorkflowAttentionRule rule) {
        return rule.thresholdDays() == null ? 0 : rule.thresholdDays();
    }
}
