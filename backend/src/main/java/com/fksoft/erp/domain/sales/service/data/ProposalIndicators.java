package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * Minimum Proposal-flow indicators (read model) over the Proposals visible to the caller. Two scopes,
 * mirroring the Opportunity indicators:
 *
 * <ul>
 *   <li><b>Volume</b> — over the Proposals created in the requested period (by creation date):
 *       {@code total}, {@code byStatus}, {@code byResponsible}, {@code proposedAmount} (the summed total of
 *       all of them), {@code acceptedAmount} (the summed total of those now ACCEPTED) and
 *       {@code rejectedCount} (those now REJECTED);
 *   <li><b>Operational snapshot</b> — a current count of all the visible Proposals, independent of the
 *       period: {@code waitingForReview} (READY_FOR_REVIEW) and {@code waitingForCustomerDecision} (SENT).
 * </ul>
 *
 * Assembled by the service from the aggregate queries plus responsible-name resolution. Exposes
 * commercial-offer figures only — never Sale, Sales Order, Booking, Financial, Payment or Commission data.
 */
public record ProposalIndicators(
        long total,
        List<StatusCount> byStatus,
        List<ResponsibleCount> byResponsible,
        BigDecimal proposedAmount,
        BigDecimal acceptedAmount,
        long rejectedCount,
        long waitingForReview,
        long waitingForCustomerDecision) {

    /** Proposal count for a lifecycle status. */
    public record StatusCount(ProposalStatus status, long count) {}

    /** Proposal count for a responsible person ({@code responsibleName == null} means unassigned). */
    public record ResponsibleCount(String responsibleName, long count) {}
}
