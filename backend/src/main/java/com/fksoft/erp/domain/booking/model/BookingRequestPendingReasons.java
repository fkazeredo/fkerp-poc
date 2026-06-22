package com.fksoft.erp.domain.booking.model;

import com.fksoft.erp.domain.booking.service.BookingRequestPendingSpecifications;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes why a Booking Request is pending. Single source of truth for the reason tags shown in the Booking
 * Operations worklist; {@link BookingRequestPendingSpecifications#pending} mirrors these predicates at the query
 * level so the page contains exactly the requests that have at least one reason. Terminal CONFIRMED / CANCELLED
 * requests are never pending. The item-derived flags ({@code hasFailedItem}, {@code hasPendingRequiredItem}) are
 * passed in (batch-resolved) to avoid a lazy load / N+1.
 */
public final class BookingRequestPendingReasons {

    /** Staleness window (days): an In Progress request whose last attempt is older than this is pending. */
    public static final int STALE_DAYS = 7;

    private BookingRequestPendingReasons() {}

    /**
     * The pending reasons that currently apply to a Booking Request (empty when it needs no action).
     *
     * @param r the booking request
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param hasFailedItem whether the request has at least one failed item (batch-resolved)
     * @param hasPendingRequiredItem whether the request has at least one requiring-booking item still pending
     * @return the matching reasons (a request may have several)
     */
    public static List<BookingPendingReason> of(
            BookingRequest r, Instant now, LocalDate today, boolean hasFailedItem, boolean hasPendingRequiredItem) {
        List<BookingPendingReason> reasons = new ArrayList<>();
        if ("CONFIRMED".equals(r.status()) || "CANCELLED".equals(r.status())) {
            return reasons;
        }
        if (r.bookingOperatorId() == null) {
            reasons.add(BookingPendingReason.UNASSIGNED_OPERATOR);
        }
        if ("PENDING".equals(r.status())) {
            reasons.add(BookingPendingReason.PENDING_WITHOUT_ATTEMPT);
        }
        Instant staleBefore = now.minus(STALE_DAYS, ChronoUnit.DAYS);
        if ("IN_PROGRESS".equals(r.status())
                && (r.lastAttemptAt() == null || r.lastAttemptAt().isBefore(staleBefore))) {
            reasons.add(BookingPendingReason.IN_PROGRESS_WITHOUT_RECENT_ATTEMPT);
        }
        if (hasFailedItem) {
            reasons.add(BookingPendingReason.HAS_FAILED_ITEM);
        }
        if (hasPendingRequiredItem) {
            reasons.add(BookingPendingReason.HAS_PENDING_REQUIRED_ITEM);
        }
        if ("PARTIALLY_CONFIRMED".equals(r.status())) {
            reasons.add(BookingPendingReason.PARTIALLY_CONFIRMED);
        }
        if (r.nextActionDate() != null && r.nextActionDate().isBefore(today)) {
            reasons.add(BookingPendingReason.OVERDUE_NEXT_ACTION);
        }
        return reasons;
    }
}
