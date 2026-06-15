package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.LeadListView;
import com.fksoft.erp.domain.crm.LeadStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational Lead list item (entity-free transport DTO). {@code unassigned} flags leads with no
 * responsible so the UI can highlight them.
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
     * Maps the domain list view to the transport DTO.
     *
     * @param v the operational list view
     * @return the response item
     */
    public static LeadListItemResponse from(LeadListView v) {
        return new LeadListItemResponse(
                v.id(),
                v.name(),
                v.mainContact(),
                v.originLabel(),
                v.status(),
                v.responsibleId(),
                v.responsibleName(),
                v.unassigned(),
                v.createdAt(),
                v.lastInteractionAt(),
                v.lastInteractionType(),
                v.nextContactAt());
    }
}
