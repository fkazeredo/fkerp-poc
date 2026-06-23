package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadInteraction;
import com.fksoft.erp.domain.crm.model.PendingLeadReasons;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Leads that need action — the OR of the active attention rules' conditions.
 * Mirrors {@link PendingLeadReasons#of}; both are driven by the same configurable
 * {@link WorkflowAttentionRule}s of the {@code lead} workflow.
 */
public final class LeadPendingSpecifications {

    private LeadPendingSpecifications() {}

    /**
     * A Lead is pending when at least one active attention rule matches.
     *
     * @param now the reference instant for "overdue"
     * @param rules the active attention rules of the {@code lead} workflow
     * @return the pending Specification
     */
    public static Specification<Lead> pending(Instant now, List<WorkflowAttentionRule> rules) {
        return (root, query, cb) -> {
            List<Predicate> ors = new ArrayList<>();
            for (WorkflowAttentionRule rule : rules) {
                Predicate predicate = predicate(rule, root, query, cb, now);
                if (predicate != null) {
                    ors.add(predicate);
                }
            }
            return ors.isEmpty() ? cb.disjunction() : cb.or(ors.toArray(Predicate[]::new));
        };
    }

    private static Predicate predicate(
            WorkflowAttentionRule rule, Root<Lead> root, CriteriaQuery<?> query, CriteriaBuilder cb, Instant now) {
        var status = root.get("status");
        return switch (rule.conditionKey()) {
            case "UNASSIGNED" -> cb.and(cb.isNull(root.get("responsiblePersonId")), cb.notEqual(status, "LOST"));
            case "NEW_WITHOUT_INTERACTION" -> {
                Subquery<UUID> interactions = query.subquery(UUID.class);
                var li = interactions.from(LeadInteraction.class);
                interactions.select(li.get("leadId")).where(cb.equal(li.get("leadId"), root.get("id")));
                yield cb.and(cb.equal(status, "NEW"), cb.not(cb.exists(interactions)));
            }
            case "OVERDUE_NEXT_CONTACT" -> cb.and(
                    cb.isNotNull(root.get("nextContactAt")),
                    cb.lessThan(root.<Instant>get("nextContactAt"), now),
                    cb.notEqual(status, "QUALIFIED"),
                    cb.notEqual(status, "LOST"));
            case "CONTACTED_WITHOUT_OUTCOME" -> cb.and(
                    cb.equal(status, "CONTACTED"), cb.isNull(root.get("nextContactAt")));
            default -> null;
        };
    }
}
