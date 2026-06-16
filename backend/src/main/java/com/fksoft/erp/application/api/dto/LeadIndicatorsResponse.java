package com.fksoft.erp.application.api.dto;

import java.util.List;

/**
 * Minimum top-of-funnel Lead indicators (entity-free transport DTO). Counts cover every status (Lost
 * included) over the Leads visible to the caller in the requested period. Assembled by the read side
 * from the aggregate queries plus responsible-name resolution.
 */
public record LeadIndicatorsResponse(
        long total,
        long newLeads,
        long contacted,
        long qualified,
        long lost,
        long waitingFirstContact,
        List<OriginCount> byOrigin,
        List<ResponsibleCount> byResponsible) {

    /** Lead count for an origin. */
    public record OriginCount(String origin, long count) {}

    /** Lead count for a responsible person ({@code responsibleName == null} means unassigned). */
    public record ResponsibleCount(String responsibleName, long count) {}
}
