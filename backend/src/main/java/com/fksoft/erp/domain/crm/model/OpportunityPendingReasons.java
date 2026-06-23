package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.OpportunityPendingSpecifications;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why an Opportunity is pending, driven by the configurable {@link WorkflowAttentionRule}s of the
 * {@code opportunity} workflow (the data-driven replacement for the former pending-reason enum). It is the
 * single source of truth for the reason tags shown in the worklist; {@link OpportunityPendingSpecifications}
 * mirrors these predicates at the query level so the page contains exactly the Opportunities that have at
 * least one reason. A WON / LOST Opportunity is terminal and never pending. "Stuck in a stage" uses the
 * creation date as the age proxy (the stage history is not consulted).
 */
public final class OpportunityPendingReasons {

    private OpportunityPendingReasons() {}

    /**
     * The pending reason codes that currently apply to an Opportunity (empty when it needs no action), one per
     * matching active rule (in rule order).
     *
     * @param opportunity the opportunity
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param lastActivityAt the most recent activity instant, or {@code null} when none (passed in to avoid an
     *     N+1)
     * @param rules the active attention rules of the {@code opportunity} workflow, in order
     * @return the matching reason codes (an Opportunity may have several)
     */
    public static List<String> of(
            Opportunity opportunity,
            Instant now,
            LocalDate today,
            Instant lastActivityAt,
            List<WorkflowAttentionRule> rules) {
        List<String> reasons = new ArrayList<>();
        if ("WON".equals(opportunity.stage()) || "LOST".equals(opportunity.stage())) {
            return reasons;
        }
        for (WorkflowAttentionRule rule : rules) {
            if (matches(rule, opportunity, now, today, lastActivityAt)) {
                reasons.add(rule.code());
            }
        }
        return reasons;
    }

    private static boolean matches(
            WorkflowAttentionRule rule, Opportunity o, Instant now, LocalDate today, Instant lastActivityAt) {
        return switch (rule.conditionKey()) {
            case "NO_RECENT_ACTIVITY" -> {
                Instant staleBefore = now.minus(days(rule), ChronoUnit.DAYS);
                boolean stale = o.createdAt().isBefore(staleBefore);
                boolean recent = lastActivityAt != null && !lastActivityAt.isBefore(staleBefore);
                yield stale && !recent;
            }
            case "NEXT_ACTION_OVERDUE" -> o.nextActionDate() != null
                    && o.nextActionDate().isBefore(today);
            case "IN_STATE_LONGER_THAN" -> rule.stateValue().equals(o.stage())
                    && o.createdAt().isBefore(now.minus(days(rule), ChronoUnit.DAYS));
            case "IN_STATE" -> rule.stateValue().equals(o.stage());
            case "EXPECTED_CLOSE_OVERDUE" -> o.expectedCloseDate() != null
                    && o.expectedCloseDate().isBefore(today);
            default -> false;
        };
    }

    private static int days(WorkflowAttentionRule rule) {
        return rule.thresholdDays() == null ? 0 : rule.thresholdDays();
    }
}
