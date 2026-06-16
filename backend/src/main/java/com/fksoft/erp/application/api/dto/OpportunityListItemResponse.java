package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.OpportunityListView;
import com.fksoft.erp.domain.crm.OpportunityStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational Opportunity list item (entity-free transport DTO). {@code unassigned} flags
 * Opportunities with no responsible so the UI can highlight them; {@code leadId} links to the source
 * Lead. Exposes commercial pipeline data only — never Proposal, Sale, Sales Order, Booking or
 * Financial data. {@code lastActivityAt} and {@code nextActionDate} are reserved for the future
 * Opportunity-activities slice and are {@code null} for now.
 */
public record OpportunityListItemResponse(
        UUID id,
        UUID leadId,
        String name,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        OpportunityStage stage,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        Instant createdAt,
        Instant lastActivityAt,
        LocalDate nextActionDate) {

    /**
     * Maps the domain list view to the transport DTO.
     *
     * @param v the operational list view
     * @return the response item
     */
    public static OpportunityListItemResponse from(OpportunityListView v) {
        return new OpportunityListItemResponse(
                v.id(),
                v.leadId(),
                v.name(),
                v.responsibleId(),
                v.responsibleName(),
                v.unassigned(),
                v.stage(),
                v.estimatedValue(),
                v.expectedCloseDate(),
                v.createdAt(),
                v.lastActivityAt(),
                v.nextActionDate());
    }
}
