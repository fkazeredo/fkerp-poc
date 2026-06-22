package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.LeadPendingSpecifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why a Lead is pending. Single source of truth for the reason tags shown in the worklist;
 * {@link LeadPendingSpecifications#pending} mirrors these predicates at the query level so the page
 * contains exactly the Leads that have at least one reason.
 */
public final class PendingLeadReasons {

    private PendingLeadReasons() {}

    /**
     * The pending reasons that currently apply to a Lead (empty when it needs no action).
     *
     * @param lead the lead
     * @param now the reference instant for "overdue"
     * @param hasInteractions whether the lead has at least one interaction
     * @return the matching reasons (a Lead may have several)
     */
    public static List<PendingReason> of(Lead lead, Instant now, boolean hasInteractions) {
        List<PendingReason> reasons = new ArrayList<>();
        String status = lead.status();
        if (lead.responsiblePersonId() == null && !"LOST".equals(status)) {
            reasons.add(PendingReason.UNASSIGNED);
        }
        if ("NEW".equals(status) && !hasInteractions) {
            reasons.add(PendingReason.NEW_WITHOUT_INTERACTION);
        }
        if (lead.nextContactAt() != null
                && lead.nextContactAt().isBefore(now)
                && !"QUALIFIED".equals(status)
                && !"LOST".equals(status)) {
            reasons.add(PendingReason.OVERDUE_NEXT_CONTACT);
        }
        if ("CONTACTED".equals(status) && lead.nextContactAt() == null) {
            reasons.add(PendingReason.CONTACTED_WITHOUT_OUTCOME);
        }
        return reasons;
    }
}
