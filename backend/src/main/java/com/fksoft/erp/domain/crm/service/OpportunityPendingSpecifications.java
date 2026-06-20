package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityActivity;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReasons;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Opportunities that need action — the OR of the pending categories (§Slice
 * 10). Mirrors {@link OpportunityPendingReasons#of} so the page contains exactly the Opportunities that
 * have at least one reason. LOST Opportunities are terminal and always excluded.
 */
public final class OpportunityPendingSpecifications {

    private OpportunityPendingSpecifications() {}

    /**
     * An Opportunity is pending (and not LOST) when it has had no commercial activity within the
     * staleness window, has an overdue next action, is stuck in NEW or DISCOVERY past the window, is
     * ready for a proposal, or its expected closing date has passed. Mirrors
     * {@link OpportunityPendingReasons#of}.
     *
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @return the pending Specification
     */
    public static Specification<Opportunity> pending(Instant now, LocalDate today) {
        return (root, query, cb) -> {
            var stage = root.get("stage");
            Instant staleBefore = now.minus(OpportunityPendingReasons.STALE_DAYS, ChronoUnit.DAYS);
            var stale = cb.lessThan(root.<Instant>get("createdAt"), staleBefore);

            Subquery<UUID> recentActivity = query.subquery(UUID.class);
            var a = recentActivity.from(OpportunityActivity.class);
            recentActivity
                    .select(a.get("opportunityId"))
                    .where(
                            cb.equal(a.get("opportunityId"), root.get("id")),
                            cb.greaterThanOrEqualTo(a.<Instant>get("occurredAt"), staleBefore));
            var withoutRecentActivity = cb.and(stale, cb.not(cb.exists(recentActivity)));

            var overdueNextAction = cb.and(
                    cb.isNotNull(root.get("nextActionDate")),
                    cb.lessThan(root.<LocalDate>get("nextActionDate"), today));

            var stuckInNew = cb.and(cb.equal(stage, OpportunityStage.NEW_OPPORTUNITY), stale);
            var stuckInDiscovery = cb.and(cb.equal(stage, OpportunityStage.DISCOVERY), stale);
            var readyForProposal = cb.equal(stage, OpportunityStage.READY_FOR_PROPOSAL);

            var expectedCloseOverdue = cb.and(
                    cb.isNotNull(root.get("expectedCloseDate")),
                    cb.lessThan(root.<LocalDate>get("expectedCloseDate"), today));

            return cb.and(
                    cb.not(stage.in(OpportunityStage.terminalStages())),
                    cb.or(
                            withoutRecentActivity,
                            overdueNextAction,
                            stuckInNew,
                            stuckInDiscovery,
                            readyForProposal,
                            expectedCloseOverdue));
        };
    }
}
