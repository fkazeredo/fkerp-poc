package com.fksoft.erp.domain.sales.model;

/**
 * Lifecycle status of a Commercial Order (Sprint 3). A new Order starts at {@link #PENDING_BOOKING} when it
 * contains items that require booking, or {@link #BOOKING_NOT_REQUIRED} otherwise. {@link #CANCELLED} is the
 * inactive state (no cancel action exists yet). An Order is "active" while it is not cancelled — at most one
 * active Order may exist per Proposal.
 */
public enum CommercialOrderStatus {
    PENDING_BOOKING,
    BOOKING_NOT_REQUIRED,
    CANCELLED;

    /**
     * Whether the Order is still active (counts against the "one active Order per Proposal" rule).
     *
     * @return {@code true} unless the Order is cancelled
     */
    public boolean isActive() {
        return this != CANCELLED;
    }
}
