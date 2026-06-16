package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational Opportunity list item (read model). {@code unassigned} flags Opportunities with no
 * responsible so the UI can highlight them; {@code leadId} links to the source Lead. Exposes commercial
 * pipeline data only — never Proposal, Sale, Sales Order, Booking or Financial data.
 * {@code lastActivityAt} and {@code nextActionDate} are reserved for the future Opportunity-activities
 * slice and are {@code null} for now.
 */
public record OpportunityListItem(
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
     * Maps an Opportunity entity (plus the responsible's resolved name) to the list item.
     * {@code lastActivityAt}/{@code nextActionDate} stay {@code null} until the Opportunity-activities
     * slice exists.
     *
     * @param o the opportunity entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @return the list item
     */
    public static OpportunityListItem from(Opportunity o, String responsibleName) {
        return new OpportunityListItem(
                o.id(),
                o.leadId(),
                o.name(),
                o.responsiblePersonId(),
                responsibleName,
                o.responsiblePersonId() == null,
                o.stage(),
                o.estimatedValue(),
                o.expectedCloseDate(),
                o.createdAt(),
                null,
                null);
    }
}
