package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Commercial Order list item (read model) for the Sales module's operational order list. {@code number} is
 * the human-friendly sequential id (rendered as PC-000n in the UI); {@code proposalTitle} is the
 * client-facing summary (the source Proposal's headline); {@code requiresBooking} is the booking-need
 * indicator (true when the Order is PENDING_BOOKING). Exposes commercial-order data plus the booking and
 * financial status reflections only — never Receivable, Payment, Commission or Customer Care detail.
 *
 * @param requiresBooking whether the Order still needs a booking operation (status PENDING_BOOKING)
 * @param bookingStatus the consolidated booking status reflected from Booking Operations, or {@code null} when
 *     no Booking Request exists yet (a read-only reflection; never drives the Order's own status)
 * @param financialStatus the Receivable status reflected from Financial Operations, or {@code null} when no
 *     Receivable exists yet ({@code PAID} = ready for Commission Management, {@code OVERDUE} = financial problem;
 *     a read-only reflection; never drives the Order's own status)
 * @param commissionStatus the commission status summary reflected from Commission Management, or {@code null} when no
 *     Commission exists yet ({@code PAID} = cycle closed, {@code ISSUE} = voided commission; a read-only reflection)
 */
public record CommercialOrderListItem(
        UUID id,
        long number,
        UUID proposalId,
        String proposalTitle,
        UUID opportunityId,
        String opportunityName,
        String status,
        UUID responsibleId,
        String responsibleName,
        boolean unassigned,
        BigDecimal total,
        boolean requiresBooking,
        String bookingStatus,
        String financialStatus,
        String commissionStatus,
        Instant createdAt) {

    /**
     * Maps a Commercial Order (plus the resolved responsible, source-proposal title and source-opportunity
     * name) to the list item.
     *
     * @param o the order entity
     * @param proposalTitle the source Proposal's title (the client-facing summary)
     * @param opportunityName the source Opportunity's name
     * @param responsibleName the responsible's display name, or {@code null} when unassigned/unknown
     * @return the list item
     */
    public static CommercialOrderListItem from(
            CommercialOrder o, String proposalTitle, String opportunityName, String responsibleName) {
        return new CommercialOrderListItem(
                o.id(),
                o.number(),
                o.proposalId(),
                proposalTitle,
                o.opportunityId(),
                opportunityName,
                o.status().name(),
                o.responsiblePersonId(),
                responsibleName,
                o.responsiblePersonId() == null,
                o.total(),
                "PENDING_BOOKING".equals(o.status().name()),
                o.bookingStatus(),
                o.financialStatus(),
                o.commissionStatus(),
                o.createdAt());
    }
}
