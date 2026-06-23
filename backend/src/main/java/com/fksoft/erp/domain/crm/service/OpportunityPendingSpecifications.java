package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivity;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReasons;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Opportunities that need action — the OR of the fixed pre-defined pending
 * conditions (excluding the terminal WON / LOST stages). Mirrors {@link OpportunityPendingReasons#of} so the
 * page contains exactly the Opportunities that have at least one reason.
 */
public final class OpportunityPendingSpecifications {

    private OpportunityPendingSpecifications() {}

    /**
     * An Opportunity is pending (and not WON / LOST) when at least one of the pre-defined reasons applies.
     *
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @return the pending Specification
     */
    public static Specification<Opportunity> pending(Instant now, LocalDate today) {
        return (root, query, cb) -> {
            Instant staleBefore = now.minus(OpportunityPendingReasons.STALENESS_DAYS, ChronoUnit.DAYS);

            Subquery<UUID> recentActivity = query.subquery(UUID.class);
            var a = recentActivity.from(OpportunityActivity.class);
            recentActivity
                    .select(a.get("opportunityId"))
                    .where(
                            cb.equal(a.get("opportunityId"), root.get("id")),
                            cb.greaterThanOrEqualTo(a.<Instant>get("occurredAt"), staleBefore));
            Predicate noRecentActivity =
                    cb.and(cb.lessThan(root.<Instant>get("createdAt"), staleBefore), cb.not(cb.exists(recentActivity)));

            Predicate nextActionOverdue = cb.and(
                    cb.isNotNull(root.get("nextActionDate")),
                    cb.lessThan(root.<LocalDate>get("nextActionDate"), today));

            Predicate stuckInNew = cb.and(
                    cb.equal(root.get("stage"), OpportunityStage.NEW_OPPORTUNITY),
                    cb.lessThan(root.<Instant>get("createdAt"), staleBefore));

            Predicate stuckInDiscovery = cb.and(
                    cb.equal(root.get("stage"), OpportunityStage.DISCOVERY),
                    cb.lessThan(root.<Instant>get("createdAt"), staleBefore));

            Predicate readyForProposal = cb.equal(root.get("stage"), OpportunityStage.READY_FOR_PROPOSAL);

            Predicate expectedCloseOverdue = cb.and(
                    cb.isNotNull(root.get("expectedCloseDate")),
                    cb.lessThan(root.<LocalDate>get("expectedCloseDate"), today));

            Predicate any = cb.or(
                    noRecentActivity,
                    nextActionOverdue,
                    stuckInNew,
                    stuckInDiscovery,
                    readyForProposal,
                    expectedCloseOverdue);
            return cb.and(cb.not(root.get("stage").in(List.of(OpportunityStage.WON, OpportunityStage.LOST))), any);
        };
    }
}
