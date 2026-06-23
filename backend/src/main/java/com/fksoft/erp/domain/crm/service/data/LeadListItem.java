package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.repository.LatestInteractionRow;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational Lead list item (read model). {@code unassigned} flags leads with no responsible so the
 * UI can highlight them. Assembled from the Lead entity plus the responsible's name (resolved from
 * Identity) and the latest-interaction row (a separate query).
 */
public record LeadListItem(
        UUID id,
        String name,
        String mainContact,
        String origin,
        String status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        Instant createdAt,
        Instant lastInteractionAt,
        String lastInteractionType,
        Instant nextContactAt) {

    /**
     * Maps a Lead entity (plus enrichment) to the list item.
     *
     * @param lead the lead entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param latest the latest interaction row for this lead, or {@code null} when none
     * @return the list item
     */
    public static LeadListItem from(Lead lead, String responsibleName, LatestInteractionRow latest) {
        return new LeadListItem(
                lead.id(),
                lead.name(),
                mainContact(lead),
                lead.origin().label(),
                lead.status().name(),
                lead.responsiblePersonId(),
                responsibleName,
                lead.responsiblePersonId() == null,
                lead.createdAt(),
                latest != null ? latest.getOccurredAt() : null,
                latest != null ? latest.getTypeLabel() : null,
                lead.nextContactAt());
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
