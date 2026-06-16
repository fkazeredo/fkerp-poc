package com.fksoft.erp.domain.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational read view of an Opportunity for the list screen. {@code responsibleId == null} means the
 * Opportunity is unassigned. {@code leadId} links to the source Lead (still the system of record for
 * contacts/history). {@code lastActivityAt} and {@code nextActionDate} are reserved for the future
 * Opportunity-activities slice and are always {@code null} for now.
 */
public record OpportunityListView(
        UUID id,
        UUID leadId,
        String name,
        UUID responsibleId,
        String responsibleName,
        OpportunityStage stage,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        Instant createdAt,
        Instant lastActivityAt,
        LocalDate nextActionDate) {

    public boolean unassigned() {
        return responsibleId == null;
    }
}
