package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.PendingReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pending Lead item (entity-free transport DTO) for the operational worklist. {@code reasons} are
 * stable codes the UI localizes; {@code unassigned} flags Leads with no responsible. Assembled from
 * the Lead entity plus the responsible's resolved name and the computed reasons.
 */
public record PendingLeadResponse(
        UUID id,
        String name,
        String mainContact,
        LeadStatus status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        Instant createdAt,
        Instant nextContactAt,
        List<PendingReason> reasons) {

    /**
     * Maps a Lead entity (plus enrichment) to the pending transport DTO.
     *
     * @param lead the lead entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param reasons the pending reasons that currently apply
     * @return the response item
     */
    public static PendingLeadResponse from(Lead lead, String responsibleName, List<PendingReason> reasons) {
        return new PendingLeadResponse(
                lead.id(),
                lead.name(),
                mainContact(lead),
                lead.status(),
                lead.responsiblePersonId(),
                responsibleName,
                lead.responsiblePersonId() == null,
                lead.createdAt(),
                lead.nextContactAt(),
                reasons);
    }

    private static String mainContact(Lead lead) {
        if (lead.phone() != null) {
            return lead.phone();
        }
        if (lead.whatsapp() != null) {
            return lead.whatsapp();
        }
        return lead.email();
    }
}
