package com.fksoft.erp.domain.booking.model;

/**
 * The per-item booking status. A requiring item starts {@code PENDING}; a non-requiring item is
 * {@code NOT_REQUIRED}. A manual attempt may move it to {@code IN_PROGRESS}; a manual confirmation to
 * {@code CONFIRMED}; a manual failure to {@code FAILED} (which may be retried). {@code CANCELLED} is reserved
 * for an explicit cancellation (a later slice). {@code CONFIRMED} and {@code CANCELLED} are the resolved
 * (terminal) states. Persisted as its name ({@code @Enumerated(STRING)}).
 */
public enum BookingItemStatus {
    PENDING,
    NOT_REQUIRED,
    IN_PROGRESS,
    CONFIRMED,
    FAILED,
    CANCELLED;

    /** Whether the item is in a resolved (terminal) state — confirmed or cancelled. */
    public boolean isResolved() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
