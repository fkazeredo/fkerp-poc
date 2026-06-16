package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.LeadIndicatorsView;
import java.util.List;

/**
 * Minimum top-of-funnel Lead indicators (entity-free transport DTO). Counts cover every status (Lost
 * included) over the Leads visible to the caller in the requested period.
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

    /**
     * Maps the domain indicators view to the transport DTO.
     *
     * @param v the indicators view
     * @return the response
     */
    public static LeadIndicatorsResponse from(LeadIndicatorsView v) {
        return new LeadIndicatorsResponse(
                v.total(),
                v.newLeads(),
                v.contacted(),
                v.qualified(),
                v.lost(),
                v.waitingFirstContact(),
                v.byOrigin().stream()
                        .map(o -> new OriginCount(o.origin(), o.count()))
                        .toList(),
                v.byResponsible().stream()
                        .map(r -> new ResponsibleCount(r.responsibleName(), r.count()))
                        .toList());
    }
}
