package com.fksoft.erp.domain.crm;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational read view of a Lead for the list screen. {@code responsibleId == null} means the Lead
 * is unassigned.
 */
public record LeadListView(
        UUID id,
        String name,
        String mainContact,
        String originLabel,
        LeadStatus status,
        UUID responsibleId,
        String responsibleName,
        Instant createdAt,
        Instant lastInteractionAt,
        String lastInteractionType,
        Instant nextContactAt) {

    public boolean unassigned() {
        return responsibleId == null;
    }
}
