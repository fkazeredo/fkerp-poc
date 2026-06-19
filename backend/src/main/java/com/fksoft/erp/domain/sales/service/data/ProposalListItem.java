package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proposal list item (read model) for the Sales module's operational list. {@code unassigned} flags
 * Proposals with no responsible; {@code opportunityId}/{@code opportunityName} link back to the source
 * Opportunity; {@code total} is the sum of the Proposal's items. Exposes commercial-offer data only —
 * never Sale, Sales Order, Booking, Financial, Payment or Commission data.
 */
public record ProposalListItem(
        UUID id,
        UUID opportunityId,
        String opportunityName,
        String title,
        ProposalStatus status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        BigDecimal total,
        LocalDate validUntil,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Maps a Proposal entity (plus the resolved responsible and source-opportunity names) to the list item.
     *
     * @param p the proposal entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param opportunityName the source Opportunity's name, or {@code null} when unknown
     * @return the list item
     */
    public static ProposalListItem from(Proposal p, String responsibleName, String opportunityName) {
        return new ProposalListItem(
                p.id(),
                p.opportunityId(),
                opportunityName,
                p.title(),
                p.status(),
                p.responsiblePersonId(),
                responsibleName,
                p.responsiblePersonId() == null,
                p.total(),
                p.validUntil(),
                p.createdAt(),
                p.updatedAt());
    }
}
