package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.OpportunityPendingSpecifications;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why an Opportunity is pending. Single source of truth for the reason tags shown in the
 * worklist; {@link OpportunityPendingSpecifications#pending} mirrors these predicates at the query level
 * so the page contains exactly the Opportunities that have at least one reason. A LOST Opportunity is
 * terminal and never pending. "Stuck in a stage" uses the creation date as the age proxy (the stage
 * history is not consulted).
 */
public final class OpportunityPendingReasons {

    /** Staleness window (days): with no recent activity / stuck in an early stage past this is pending. */
    public static final int STALE_DAYS = 14;

    private OpportunityPendingReasons() {}

    /**
     * The pending reasons that currently apply to an Opportunity (empty when it needs no action).
     *
     * @param opportunity the opportunity
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param lastActivityAt the most recent activity instant, or {@code null} when none (passed in to
     *     avoid a lazy load / N+1)
     * @return the matching reasons (an Opportunity may have several)
     */
    public static List<OpportunityPendingReason> of(
            Opportunity opportunity, Instant now, LocalDate today, Instant lastActivityAt) {
        List<OpportunityPendingReason> reasons = new ArrayList<>();
        if (opportunity.stage().isTerminal()) {
            return reasons;
        }
        Instant staleBefore = now.minus(STALE_DAYS, ChronoUnit.DAYS);
        boolean stale = opportunity.createdAt().isBefore(staleBefore);
        boolean hasRecentActivity = lastActivityAt != null && !lastActivityAt.isBefore(staleBefore);

        if (stale && !hasRecentActivity) {
            reasons.add(OpportunityPendingReason.WITHOUT_RECENT_ACTIVITY);
        }
        if (opportunity.nextActionDate() != null && opportunity.nextActionDate().isBefore(today)) {
            reasons.add(OpportunityPendingReason.OVERDUE_NEXT_ACTION);
        }
        if (opportunity.stage() == OpportunityStage.NEW_OPPORTUNITY && stale) {
            reasons.add(OpportunityPendingReason.STUCK_IN_NEW);
        }
        if (opportunity.stage() == OpportunityStage.DISCOVERY && stale) {
            reasons.add(OpportunityPendingReason.STUCK_IN_DISCOVERY);
        }
        if (opportunity.stage() == OpportunityStage.READY_FOR_PROPOSAL) {
            reasons.add(OpportunityPendingReason.READY_FOR_PROPOSAL);
        }
        if (opportunity.expectedCloseDate() != null
                && opportunity.expectedCloseDate().isBefore(today)) {
            reasons.add(OpportunityPendingReason.EXPECTED_CLOSE_OVERDUE);
        }
        return reasons;
    }
}
