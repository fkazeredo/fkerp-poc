package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityPendingReason;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pending Opportunity item (read model) for the operational worklist. {@code reasons} are stable codes
 * the UI localizes; {@code unassigned} flags Opportunities with no responsible. Assembled from the
 * Opportunity entity plus the responsible's resolved name, the latest activity instant and the computed
 * reasons. Exposes commercial pipeline data only — never Proposal, Sale, Sales Order, Booking or
 * Financial data.
 */
public record PendingOpportunity(
        UUID id,
        UUID leadId,
        String name,
        OpportunityStage stage,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        LocalDate nextActionDate,
        Instant createdAt,
        Instant lastActivityAt,
        List<OpportunityPendingReason> reasons) {

    /**
     * Maps an Opportunity entity (plus enrichment) to the pending item.
     *
     * @param o the opportunity entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param lastActivityAt the most recent activity's instant, or {@code null} when none
     * @param reasons the pending reasons that currently apply
     * @return the pending item
     */
    public static PendingOpportunity from(
            Opportunity o, String responsibleName, Instant lastActivityAt, List<OpportunityPendingReason> reasons) {
        return new PendingOpportunity(
                o.id(),
                o.leadId(),
                o.name(),
                o.stage(),
                o.responsiblePersonId(),
                responsibleName,
                o.responsiblePersonId() == null,
                o.estimatedValue(),
                o.expectedCloseDate(),
                o.nextActionDate(),
                o.createdAt(),
                lastActivityAt,
                reasons);
    }
}
