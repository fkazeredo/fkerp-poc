package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import java.util.List;

/**
 * Minimum Booking Operations indicators (read model) over the Booking Requests visible to the caller. Two
 * scopes, mirroring the other indicator views:
 *
 * <ul>
 *   <li><b>Volume</b> — over the requests created in the requested period (by creation date): {@code total},
 *       {@code byStatus}, {@code itemsByType}, {@code failedItems} and {@code avgConfirmationSeconds};
 *   <li><b>Operational snapshot</b> — a current count of all the visible requests, independent of the period:
 *       {@code readyForFinance} (requests CONFIRMED now — ready for Financial Operations).
 * </ul>
 *
 * Exposes operational reservation figures only — <b>never</b> Financial, Payment, Commission, Customer Care or
 * external-integration data.
 *
 * @param total the number of requests created in the period
 * @param byStatus the request counts per status (pending / in progress / partially confirmed / confirmed /
 *     failed / cancelled) over the period
 * @param itemsByType the booking-item counts per item type over the period
 * @param failedItems the number of failed booking items over the period
 * @param readyForFinance the current number of CONFIRMED requests (ready for Financial Operations), a snapshot
 * @param avgConfirmationSeconds the average creation→confirmation time in seconds over the requests confirmed in
 *     the period, or {@code null} when there is no confirmed data yet
 */
public record BookingIndicators(
        long total,
        List<StatusCount> byStatus,
        List<ItemTypeCount> itemsByType,
        long failedItems,
        long readyForFinance,
        Long avgConfirmationSeconds) {

    /** Booking Request count for a lifecycle status. */
    public record StatusCount(BookingRequestStatus status, long count) {}

    /** Booking item count for an item type. */
    public record ItemTypeCount(ProposalItemType type, long count) {}
}
