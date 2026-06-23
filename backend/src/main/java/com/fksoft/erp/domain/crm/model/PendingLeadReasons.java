package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.LeadPendingSpecifications;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why a Lead is pending, driven by the configurable {@link WorkflowAttentionRule}s of the
 * {@code lead} workflow (the data-driven replacement for the former pending-reason enum). Single source of
 * truth for the reason tags shown in the worklist; {@link LeadPendingSpecifications#pending} mirrors these
 * predicates at the query level so the page contains exactly the Leads that have at least one reason.
 */
public final class PendingLeadReasons {

    private PendingLeadReasons() {}

    /**
     * The pending reason codes that currently apply to a Lead (empty when it needs no action), one per
     * matching active rule (in rule order).
     *
     * @param lead the lead
     * @param now the reference instant for "overdue"
     * @param hasInteractions whether the lead has at least one interaction
     * @param rules the active attention rules of the {@code lead} workflow, in order
     * @return the matching reason codes (a Lead may have several)
     */
    public static List<String> of(Lead lead, Instant now, boolean hasInteractions, List<WorkflowAttentionRule> rules) {
        List<String> reasons = new ArrayList<>();
        String status = lead.status();
        for (WorkflowAttentionRule rule : rules) {
            if (matches(rule, lead, now, hasInteractions, status)) {
                reasons.add(rule.code());
            }
        }
        return reasons;
    }

    private static boolean matches(
            WorkflowAttentionRule rule, Lead lead, Instant now, boolean hasInteractions, String status) {
        return switch (rule.conditionKey()) {
            case "UNASSIGNED" -> lead.responsiblePersonId() == null && !"LOST".equals(status);
            case "NEW_WITHOUT_INTERACTION" -> "NEW".equals(status) && !hasInteractions;
            case "OVERDUE_NEXT_CONTACT" -> lead.nextContactAt() != null
                    && lead.nextContactAt().isBefore(now)
                    && !"QUALIFIED".equals(status)
                    && !"LOST".equals(status);
            case "CONTACTED_WITHOUT_OUTCOME" -> "CONTACTED".equals(status) && lead.nextContactAt() == null;
            default -> false;
        };
    }
}
