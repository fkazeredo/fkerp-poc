package com.fksoft.erp.domain.commission.model;

/**
 * The basis the Expected Commission amount was calculated from:
 *
 * <ul>
 *   <li>{@code COMMERCIAL_AMOUNT} — the Order's commercial total (a forecast, used when no payment was received yet);
 *   <li>{@code RECEIVED_AMOUNT} — the amount already received on the Order's Receivable (used when it is available).
 * </ul>
 *
 * Persisted as its name ({@code @Enumerated(STRING)}, mirrored by a DB {@code CHECK}); the name is the value in the
 * JSON contract.
 */
public enum CommissionBasis {
    COMMERCIAL_AMOUNT,
    RECEIVED_AMOUNT
}
