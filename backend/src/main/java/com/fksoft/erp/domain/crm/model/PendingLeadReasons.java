package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.LeadPendingSpecifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why a Lead is pending — the fixed, pre-defined set of worklist reasons for the Lead lifecycle.
 * Single source of truth for the reason tags shown in the worklist; {@link LeadPendingSpecifications#pending}
 * mirrors these predicates at the query level so the page contains exactly the Leads that have at least one
 * reason.
 */
public final class PendingLeadReasons {

    /** A Lead with no responsible (and not lost) needs assignment. */
    public static final String UNASSIGNED = "UNASSIGNED";

    /** A brand-new Lead that has never been worked. */
    public static final String NEW_WITHOUT_INTERACTION = "NEW_WITHOUT_INTERACTION";

    /** The scheduled next contact is in the past (and the Lead is still active). */
    public static final String OVERDUE_NEXT_CONTACT = "OVERDUE_NEXT_CONTACT";

    /** A contacted Lead with no scheduled next contact — it risks being forgotten. */
    public static final String CONTACTED_WITHOUT_OUTCOME = "CONTACTED_WITHOUT_OUTCOME";

    private PendingLeadReasons() {}

    /**
     * The pending reason codes that currently apply to a Lead (empty when it needs no action), in display
     * order. A Lead may have several.
     *
     * @param lead the lead
     * @param now the reference instant for "overdue"
     * @param hasInteractions whether the lead has at least one interaction
     * @return the matching reason codes
     */
    public static List<String> of(Lead lead, Instant now, boolean hasInteractions) {
        List<String> reasons = new ArrayList<>();
        LeadStatus status = lead.status();
        if (lead.responsiblePersonId() == null && status != LeadStatus.LOST) {
            reasons.add(UNASSIGNED);
        }
        if (status == LeadStatus.NEW && !hasInteractions) {
            reasons.add(NEW_WITHOUT_INTERACTION);
        }
        if (lead.nextContactAt() != null
                && lead.nextContactAt().isBefore(now)
                && status != LeadStatus.QUALIFIED
                && status != LeadStatus.LOST) {
            reasons.add(OVERDUE_NEXT_CONTACT);
        }
        if (status == LeadStatus.CONTACTED && lead.nextContactAt() == null) {
            reasons.add(CONTACTED_WITHOUT_OUTCOME);
        }
        return reasons;
    }
}
