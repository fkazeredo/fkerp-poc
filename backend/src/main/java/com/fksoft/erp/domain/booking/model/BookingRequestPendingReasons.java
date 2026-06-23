package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.service.BookingRequestPendingSpecifications;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why a Booking Request is pending — the fixed, pre-defined set of worklist reasons for the Booking
 * Operations lifecycle. Single source of truth for the reason tags shown in the worklist;
 * {@link BookingRequestPendingSpecifications#pending} mirrors these predicates at the query level so the page
 * contains exactly the requests that have at least one reason. Terminal CONFIRMED / CANCELLED requests are
 * never pending. The item-derived flags ({@code hasFailedItem}, {@code hasPendingRequiredItem}) are passed in
 * (batch-resolved) to avoid a lazy load / N+1.
 */
public final class BookingRequestPendingReasons {

    /** Staleness window (days) for an IN_PROGRESS request without a recent attempt. */
    public static final int STALENESS_DAYS = 7;

    /** No booking operator assigned. */
    public static final String UNASSIGNED_OPERATOR = "UNASSIGNED_OPERATOR";

    /** Still PENDING — no attempt has been made. */
    public static final String PENDING_WITHOUT_ATTEMPT = "PENDING_WITHOUT_ATTEMPT";

    /** In progress but no attempt within the staleness window. */
    public static final String IN_PROGRESS_WITHOUT_RECENT_ATTEMPT = "IN_PROGRESS_WITHOUT_RECENT_ATTEMPT";

    /** At least one item has failed. */
    public static final String HAS_FAILED_ITEM = "HAS_FAILED_ITEM";

    /** At least one requiring-booking item is still pending. */
    public static final String HAS_PENDING_REQUIRED_ITEM = "HAS_PENDING_REQUIRED_ITEM";

    /** Only partially confirmed — the rest still needs work. */
    public static final String PARTIALLY_CONFIRMED = "PARTIALLY_CONFIRMED";

    /** A planned next action whose date is past. */
    public static final String OVERDUE_NEXT_ACTION = "OVERDUE_NEXT_ACTION";

    private BookingRequestPendingReasons() {}

    /**
     * The pending reason codes that currently apply to a Booking Request (empty when it needs no action), in
     * display order. A request may have several.
     *
     * @param r the booking request
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param hasFailedItem whether the request has at least one failed item (batch-resolved)
     * @param hasPendingRequiredItem whether the request has at least one requiring-booking item still pending
     * @return the matching reason codes
     */
    public static List<String> of(
            BookingRequest r, Instant now, LocalDate today, boolean hasFailedItem, boolean hasPendingRequiredItem) {
        List<String> reasons = new ArrayList<>();
        BookingRequestStatus status = r.status();
        if (status == BookingRequestStatus.CONFIRMED || status == BookingRequestStatus.CANCELLED) {
            return reasons;
        }
        if (r.bookingOperatorId() == null) {
            reasons.add(UNASSIGNED_OPERATOR);
        }
        if (status == BookingRequestStatus.PENDING) {
            reasons.add(PENDING_WITHOUT_ATTEMPT);
        }
        if (status == BookingRequestStatus.IN_PROGRESS
                && (r.lastAttemptAt() == null
                        || r.lastAttemptAt().isBefore(now.minus(STALENESS_DAYS, ChronoUnit.DAYS)))) {
            reasons.add(IN_PROGRESS_WITHOUT_RECENT_ATTEMPT);
        }
        if (hasFailedItem) {
            reasons.add(HAS_FAILED_ITEM);
        }
        if (hasPendingRequiredItem) {
            reasons.add(HAS_PENDING_REQUIRED_ITEM);
        }
        if (status == BookingRequestStatus.PARTIALLY_CONFIRMED) {
            reasons.add(PARTIALLY_CONFIRMED);
        }
        if (r.nextActionDate() != null && r.nextActionDate().isBefore(today)) {
            reasons.add(OVERDUE_NEXT_ACTION);
        }
        return reasons;
    }
}
