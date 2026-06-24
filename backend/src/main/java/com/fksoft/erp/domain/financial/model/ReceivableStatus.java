package com.fksoft.erp.domain.financial.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Receivable lifecycle (Financial Operations), a fixed state machine:
 *
 * <ul>
 *   <li>{@code OPEN} — created, not yet fully paid;
 *   <li>{@code PARTIALLY_PAID} — part of the amount was received;
 *   <li>{@code PAID} — the full amount was received;
 *   <li>{@code OVERDUE} — past due and not fully paid;
 *   <li>{@code CANCELLED} — cancelled by an authorized operational decision.
 * </ul>
 *
 * Payment registration and the transitions beyond {@code OPEN} are later slices. Persisted as its name
 * ({@code @Enumerated(STRING)}, mirrored by a DB {@code CHECK}); the name is the value in the JSON contract.
 */
public enum ReceivableStatus {
    OPEN,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED;

    /** The active statuses (everything but CANCELLED) — for the "one active Receivable per Order" rule. */
    public static Set<ReceivableStatus> active() {
        return EnumSet.complementOf(EnumSet.of(CANCELLED));
    }

    /**
     * The statuses that still require financial follow-up — shown in the default operational list and the only
     * ones that can be flagged overdue. Excludes the settled outcomes ({@code PAID}, {@code CANCELLED}).
     *
     * @return the operational statuses ({@code OPEN}, {@code PARTIALLY_PAID}, {@code OVERDUE})
     */
    public static Set<ReceivableStatus> operational() {
        return EnumSet.of(OPEN, PARTIALLY_PAID, OVERDUE);
    }
}
