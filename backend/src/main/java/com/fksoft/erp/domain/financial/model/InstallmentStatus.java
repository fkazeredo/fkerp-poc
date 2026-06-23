package com.fksoft.erp.domain.financial.model;

/**
 * The lifecycle of a single {@link ReceivableInstallment} (Financial Operations), a fixed state machine
 * mirroring {@link ReceivableStatus}:
 *
 * <ul>
 *   <li>{@code OPEN} — scheduled, not yet paid;
 *   <li>{@code PARTIALLY_PAID} — part of the installment amount was received;
 *   <li>{@code PAID} — the installment was fully received;
 *   <li>{@code OVERDUE} — past due and not fully paid;
 *   <li>{@code CANCELLED} — cancelled by an authorized operational decision.
 * </ul>
 *
 * Installments start {@code OPEN}; the transitions beyond {@code OPEN} are driven by payment behavior, which is a
 * later slice. Persisted as its name ({@code @Enumerated(STRING)}, mirrored by a DB {@code CHECK}); the name is
 * the value in the JSON contract.
 */
public enum InstallmentStatus {
    OPEN,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    CANCELLED
}
