package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Opportunity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational Opportunity list item (read model). {@code unassigned} flags Opportunities with no
 * responsible so the UI can highlight them; {@code leadId} links to the source Lead. Exposes commercial
 * pipeline data only — never Proposal, Sale, Sales Order, Booking or Financial data.
 * {@code lastActivityAt} is the most recent activity's instant; {@code nextActionDate} is the planned
 * next action — both {@code null} until an activity is registered.
 */
public record OpportunityListItem(
        UUID id,
        UUID leadId,
        String name,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        String stage,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        Instant createdAt,
        Instant lastActivityAt,
        LocalDate nextActionDate) {

    /**
     * Maps an Opportunity entity (plus the responsible's resolved name and the latest activity instant)
     * to the list item.
     *
     * @param o the opportunity entity
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @param lastActivityAt the most recent activity's instant, or {@code null} when none
     * @return the list item
     */
    public static OpportunityListItem from(Opportunity o, String responsibleName, Instant lastActivityAt) {
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
                lastActivityAt,
                o.nextActionDate());
    }
}
