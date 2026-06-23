package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pending Booking Request item (read model) for the Booking Operations worklist. Mirrors the operational list
 * item plus the planned {@code nextActionDate}, the {@code failedItems} count and the computed {@code reasons}
 * (stable codes the UI localizes). The source Commercial Order's number ({@code commercialOrderNumber}, rendered
 * PC-000n) is the human identifier. Carries operational reservation data only — <b>never</b> Financial, Payment
 * or Commission data.
 *
 * @param reasons why the request needs action (a request may have several reasons)
 */
public record PendingBookingRequest(
        UUID id,
        UUID commercialOrderId,
        long commercialOrderNumber,
        UUID proposalId,
        String proposalTitle,
        String status,
        UUID bookingOperatorId,
        String bookingOperatorName,
        boolean operatorUnassigned,
        UUID responsiblePersonId,
        String responsibleName,
        long itemsRequiringBooking,
        long confirmedItems,
        long failedItems,
        Instant lastBookingAttemptAt,
        LocalDate nextActionDate,
        Instant createdAt,
        Instant updatedAt,
        List<String> reasons) {

    /**
     * Maps a Booking Request (plus the resolved enrichment and the computed reasons) to the pending item.
     *
     * @param r the booking request entity
     * @param orderNumber the source Commercial Order's sequential number (the human identifier, PC-000n)
     * @param proposalTitle the source Proposal's title (the commercial reference)
     * @param operatorName the booking operator's display name, or {@code null} when unassigned/unknown
     * @param responsibleName the commercial responsible's display name, or {@code null} when unassigned/unknown
     * @param itemCounts the item counts {@code [requiring, confirmed, failed]}
     * @param reasons the pending reasons that currently apply
     * @return the pending item
     */
    public static PendingBookingRequest from(
            BookingRequest r,
            long orderNumber,
            String proposalTitle,
            String operatorName,
            String responsibleName,
            long[] itemCounts,
            List<String> reasons) {
        return new PendingBookingRequest(
                r.id(),
                r.commercialOrderId(),
                orderNumber,
                r.proposalId(),
                proposalTitle,
                r.status(),
                r.bookingOperatorId(),
                operatorName,
                r.bookingOperatorId() == null,
                r.responsiblePersonId(),
                responsibleName,
                itemCounts[0],
                itemCounts[1],
                itemCounts[2],
                r.lastAttemptAt(),
                r.nextActionDate(),
                r.createdAt(),
                r.updatedAt(),
                reasons);
    }
}
