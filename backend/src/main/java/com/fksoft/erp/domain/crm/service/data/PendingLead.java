package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pending Lead item (read model) for the operational worklist. {@code reasons} are stable codes the UI
 * localizes; {@code unassigned} flags Leads with no responsible. Assembled from the Lead entity plus
 * the responsible's resolved name and the computed reasons.
 */
public record PendingLead(
        UUID id,
        String name,
        String mainContact,
        String status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        Instant createdAt,
        Instant nextContactAt,
        List<String> reasons) {

    /**
     * Maps a Lead entity (plus enrichment) to the pending item.
     *
     * @param lead the lead entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param reasons the pending reasons that currently apply
     * @return the pending item
     */
    public static PendingLead from(Lead lead, String responsibleName, List<String> reasons) {
        return new PendingLead(
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
