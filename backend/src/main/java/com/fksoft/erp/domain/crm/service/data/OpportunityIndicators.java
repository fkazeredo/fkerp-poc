package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.OpportunityStage;
import java.math.BigDecimal;
import java.util.List;

/**
 * Minimum commercial-pipeline indicators (read model) over the Opportunities visible to the caller. Two
 * scopes, like a mainstream CRM/ERP pipeline view:
 *
 * <ul>
 *   <li><b>Volume</b> — over the Opportunities created in the requested period (by creation date):
 *       {@code total}, {@code lost}, {@code byStage}, {@code byOrigin}, {@code byResponsible};
 *   <li><b>Pipeline</b> — a current snapshot of all the visible Opportunities (not period-filtered):
 *       {@code active} (non-LOST), {@code readyForProposal}, {@code overdueClose},
 *       {@code activePipelineValue} and {@code valueByResponsible}.
 * </ul>
 *
 * Assembled by the service from the aggregate queries plus responsible-name resolution. Exposes
 * commercial pipeline data only — never Proposal, Sale, Sales Order, Booking, Financial or Commission
 * data.
 */
public record OpportunityIndicators(
        long total,
        long lost,
        List<StageCount> byStage,
        List<OriginCount> byOrigin,
        List<ResponsibleCount> byResponsible,
        long active,
        long readyForProposal,
        long overdueClose,
        BigDecimal activePipelineValue,
        List<ResponsibleValue> valueByResponsible) {

    /** Opportunity count for a pipeline stage. */
    public record StageCount(OpportunityStage stage, long count) {}

    /** Opportunity count for a Lead origin. */
    public record OriginCount(String origin, long count) {}

    /** Opportunity count for a responsible person ({@code responsibleName == null} means unassigned). */
    public record ResponsibleCount(String responsibleName, long count) {}

    /**
     * Active-pipeline estimated value for a responsible person ({@code responsibleName == null} means
     * unassigned).
     */
    public record ResponsibleValue(String responsibleName, BigDecimal value) {}
}
