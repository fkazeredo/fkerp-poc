package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proposal list item (read model) for the Sales module's operational list. {@code unassigned} flags
 * Proposals with no responsible; {@code opportunityId} links back to the source Opportunity. Exposes
 * commercial-offer data only — never Sale, Sales Order, Booking or Financial data.
 */
public record ProposalListItem(
        UUID id,
        UUID opportunityId,
        String title,
        ProposalStatus status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        LocalDate validUntil,
        Instant createdAt) {

    /**
     * Maps a Proposal entity (plus the responsible's resolved name) to the list item.
     *
     * @param p the proposal entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @return the list item
     */
    public static ProposalListItem from(Proposal p, String responsibleName) {
        return new ProposalListItem(
                p.id(),
                p.opportunityId(),
                p.title(),
                p.status(),
                p.responsiblePersonId(),
                responsibleName,
                p.responsiblePersonId() == null,
                p.validUntil(),
                p.createdAt());
    }
}
