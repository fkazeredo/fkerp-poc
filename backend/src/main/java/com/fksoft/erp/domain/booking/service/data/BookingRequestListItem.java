package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingRequest;
import java.time.Instant;
import java.util.UUID;

/**
 * Booking Request list item (read model) for the operational reservation worklist (Booking Operations).
 *
 * <p>The reservation has no human number of its own (Rule Zero): the source Commercial Order's sequential
 * number ({@code commercialOrderNumber}, rendered PC-000n in the UI) is the human-friendly identifier, 1:1
 * with the active Order. {@code proposalTitle} is the commercial reference (the source Proposal headline).
 * {@code itemsRequiringBooking} / {@code confirmedItems} are the line counts. {@code lastBookingAttemptAt} is
 * the most recent manual attempt's instant (denormalized; {@code null} until the first attempt is registered).
 * Carries operational reservation data only — <b>never</b> Financial, Payment or Commission data.
 *
 * @param lastBookingAttemptAt the latest manual booking attempt instant, or {@code null} when there is none
 */
public record BookingRequestListItem(
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
        Instant createdAt,
        Instant updatedAt,
        Instant lastBookingAttemptAt) {

    /**
     * Maps a Booking Request (plus the resolved source-order number, source-proposal title, operator and
     * commercial-responsible names, and the item counts) to the list item.
     *
     * @param r the booking request entity
     * @param orderNumber the source Commercial Order's sequential number (the human identifier, PC-000n)
     * @param proposalTitle the source Proposal's title (the commercial reference)
     * @param operatorName the booking operator's display name, or {@code null} when unassigned/unknown
     * @param responsibleName the commercial responsible's display name, or {@code null} when unassigned/unknown
     * @param requiringCount how many of its items require booking
     * @param confirmedCount how many of its items are confirmed
     * @return the list item
     */
    public static BookingRequestListItem from(
            BookingRequest r,
            long orderNumber,
            String proposalTitle,
            String operatorName,
            String responsibleName,
            long requiringCount,
            long confirmedCount) {
        return new BookingRequestListItem(
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
                requiringCount,
                confirmedCount,
                r.createdAt(),
                r.updatedAt(),
                r.lastAttemptAt());
    }
}
