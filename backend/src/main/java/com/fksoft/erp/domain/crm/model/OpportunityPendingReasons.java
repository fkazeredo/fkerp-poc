package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.OpportunityPendingSpecifications;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why an Opportunity is pending — the fixed, pre-defined set of worklist reasons for the Opportunity
 * pipeline. Single source of truth for the reason tags shown in the worklist;
 * {@link OpportunityPendingSpecifications} mirrors these predicates at the query level so the page contains
 * exactly the Opportunities that have at least one reason. A WON / LOST Opportunity is terminal and never
 * pending. "Stuck in a stage" uses the creation date as the age proxy (the stage history is not consulted).
 */
public final class OpportunityPendingReasons {

    /** Staleness window (days) for "no recent activity" and "stuck in an early stage". */
    public static final int STALENESS_DAYS = 14;

    /** No activity within the staleness window. */
    public static final String WITHOUT_RECENT_ACTIVITY = "WITHOUT_RECENT_ACTIVITY";

    /** A planned next action whose date is past. */
    public static final String OVERDUE_NEXT_ACTION = "OVERDUE_NEXT_ACTION";

    /** Stuck in the initial stage beyond the staleness window. */
    public static final String STUCK_IN_NEW = "STUCK_IN_NEW";

    /** Stuck in Discovery beyond the staleness window. */
    public static final String STUCK_IN_DISCOVERY = "STUCK_IN_DISCOVERY";

    /** Ready for a proposal — waiting to be advanced into Sales. */
    public static final String READY_FOR_PROPOSAL = "READY_FOR_PROPOSAL";

    /** The expected closing date is past. */
    public static final String EXPECTED_CLOSE_OVERDUE = "EXPECTED_CLOSE_OVERDUE";

    private OpportunityPendingReasons() {}

    /**
     * The pending reason codes that currently apply to an Opportunity (empty when it needs no action), in
     * display order. An Opportunity may have several.
     *
     * @param o the opportunity
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param lastActivityAt the most recent activity instant, or {@code null} when none (passed in to avoid an
     *     N+1)
     * @return the matching reason codes
     */
    public static List<String> of(Opportunity o, Instant now, LocalDate today, Instant lastActivityAt) {
        List<String> reasons = new ArrayList<>();
        if (o.stage().isTerminal()) {
            return reasons;
        }
        Instant staleBefore = now.minus(STALENESS_DAYS, ChronoUnit.DAYS);
        boolean recent = lastActivityAt != null && !lastActivityAt.isBefore(staleBefore);
        if (o.createdAt().isBefore(staleBefore) && !recent) {
            reasons.add(WITHOUT_RECENT_ACTIVITY);
        }
        if (o.nextActionDate() != null && o.nextActionDate().isBefore(today)) {
            reasons.add(OVERDUE_NEXT_ACTION);
        }
        if (o.stage() == OpportunityStage.NEW_OPPORTUNITY && o.createdAt().isBefore(staleBefore)) {
            reasons.add(STUCK_IN_NEW);
        }
        if (o.stage() == OpportunityStage.DISCOVERY && o.createdAt().isBefore(staleBefore)) {
            reasons.add(STUCK_IN_DISCOVERY);
        }
        if (o.stage() == OpportunityStage.READY_FOR_PROPOSAL) {
            reasons.add(READY_FOR_PROPOSAL);
        }
        if (o.expectedCloseDate() != null && o.expectedCloseDate().isBefore(today)) {
            reasons.add(EXPECTED_CLOSE_OVERDUE);
        }
        return reasons;
    }
}
