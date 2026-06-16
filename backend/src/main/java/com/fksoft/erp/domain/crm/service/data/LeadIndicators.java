package com.fksoft.erp.domain.crm.service.data;

import java.util.List;

/**
 * Minimum top-of-funnel Lead indicators (read model). Counts cover every status (Lost included) over
 * the Leads visible to the caller in the requested period. Assembled by the service from the aggregate
 * queries plus responsible-name resolution.
 */
public record LeadIndicators(
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
