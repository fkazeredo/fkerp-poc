package com.fksoft.erp.domain.sales.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Commercial Order lifecycle, a fixed set of states. A new Order starts at {@code PENDING_BOOKING} (when
 * it has a bookable item) or {@code BOOKING_NOT_REQUIRED} otherwise — both are entry states — and
 * {@code CANCELLED} is the terminal-negative ("not active") state (the cancel action is a later slice). The
 * initial-state selection is the Order's own logic. Persisted as its name ({@code @Enumerated(STRING)}).
 */
public enum CommercialOrderStatus {
    PENDING_BOOKING,
    BOOKING_NOT_REQUIRED,
    CANCELLED;

    /** Whether the Order is still active (not cancelled). */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /** The active statuses (for the "at most one active Order per Proposal" query). */
    public static Set<CommercialOrderStatus> active() {
        return EnumSet.of(PENDING_BOOKING, BOOKING_NOT_REQUIRED);
    }
}
