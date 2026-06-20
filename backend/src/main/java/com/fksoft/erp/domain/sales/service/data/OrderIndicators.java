package com.fksoft.erp.domain.sales.service.data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimum Commercial Order indicators (read model) over the Orders visible to the caller. Two scopes,
 * mirroring the Proposal indicators:
 *
 * <ul>
 *   <li><b>Volume</b> — over the Orders created in the requested period (by creation date): {@code total},
 *       {@code totalAmount} (the summed Order total) and {@code byResponsible};
 *   <li><b>Operational snapshot</b> — a current count of all the visible Orders, independent of the period:
 *       {@code pendingBooking} (Orders still PENDING_BOOKING).
 * </ul>
 *
 * Assembled by the service from the aggregate queries plus responsible-name resolution. Exposes
 * commercial-order figures only — never Booking, Receivable, Payment, Commission or Customer Care data.
 */
public record OrderIndicators(
        long total, BigDecimal totalAmount, List<ResponsibleCount> byResponsible, long pendingBooking) {

    /** Commercial Order count for a responsible person ({@code responsibleName == null} means unassigned). */
    public record ResponsibleCount(String responsibleName, long count) {}
}
