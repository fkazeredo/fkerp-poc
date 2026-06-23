package com.fksoft.erp.domain.booking.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Booking Request status. It is <b>state-derived</b> — computed by {@link BookingRequest}'s
 * consolidation from its items requiring booking plus the attempt history (every requiring item confirmed →
 * {@code CONFIRMED}; some but not all → {@code PARTIALLY_CONFIRMED}; none confirmed but one failed →
 * {@code FAILED}; nothing resolved but at least one attempt → {@code IN_PROGRESS}; otherwise
 * {@code PENDING}). {@code CANCELLED} is reserved for an explicit cancellation (a later slice) and is never
 * overridden by the consolidation. Persisted as its name ({@code @Enumerated(STRING)}).
 */
public enum BookingRequestStatus {
    PENDING,
    IN_PROGRESS,
    PARTIALLY_CONFIRMED,
    CONFIRMED,
    FAILED,
    CANCELLED;

    /** The active statuses (everything but CANCELLED) — for the "one active request per Order" query. */
    public static Set<BookingRequestStatus> active() {
        return EnumSet.complementOf(EnumSet.of(CANCELLED));
    }
}
