package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.LatestInteractionRow;
import com.fksoft.erp.domain.crm.Lead;
import com.fksoft.erp.domain.crm.LeadStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational Lead list item (entity-free transport DTO). {@code unassigned} flags leads with no
 * responsible so the UI can highlight them. Assembled from the Lead entity plus the responsible's name
 * (resolved from Identity) and the latest-interaction row (a separate query).
 */
public record LeadListItemResponse(
        UUID id,
        String name,
        String mainContact,
        String origin,
        LeadStatus status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        Instant createdAt,
        Instant lastInteractionAt,
        String lastInteractionType,
        Instant nextContactAt) {

    /**
     * Maps a Lead entity (plus enrichment) to the transport DTO.
     *
     * @param lead the lead entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param latest the latest interaction row for this lead, or {@code null} when none
     * @return the response item
     */
    public static LeadListItemResponse from(Lead lead, String responsibleName, LatestInteractionRow latest) {
        return new LeadListItemResponse(
                lead.id(),
                lead.name(),
                mainContact(lead),
                lead.origin().label(),
                lead.status(),
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
