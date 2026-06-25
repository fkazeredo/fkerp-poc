package com.fksoft.erp.domain.commission.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Commission lifecycle (Commission Management), a fixed enum state machine:
 *
 * <ul>
 *   <li>{@code EXPECTED} — generated as a forecast from a closed Commercial Order (not payable yet);
 *   <li>{@code ELIGIBLE} — the source deal's Receivable is paid, so the commission may be approved (a later slice);
 *   <li>{@code APPROVED} — an authorized manager approved it (a later slice);
 *   <li>{@code REJECTED} — an authorized manager rejected it (a later slice);
 *   <li>{@code PAID} — the commission payment was registered (a later slice);
 *   <li>{@code CANCELLED} — cancelled by an authorized decision (a later slice).
 * </ul>
 *
 * Slice 2 generates only {@code EXPECTED}; the transitions are later slices. Persisted as its name
 * ({@code @Enumerated(STRING)}, mirrored by a DB {@code CHECK}); the name is the value in the JSON contract.
 */
public enum CommissionStatus {
    EXPECTED,
    ELIGIBLE,
    APPROVED,
    REJECTED,
    PAID,
    CANCELLED;

    /**
     * The active statuses for the "at most one active Commission per Order" rule — everything but the terminal
     * dead states ({@code REJECTED}/{@code CANCELLED}). A new commission may be generated for an Order only once
     * any previous one is rejected or cancelled.
     *
     * @return the active commission statuses
     */
    public static Set<CommissionStatus> active() {
        return EnumSet.complementOf(EnumSet.of(REJECTED, CANCELLED));
    }
}
