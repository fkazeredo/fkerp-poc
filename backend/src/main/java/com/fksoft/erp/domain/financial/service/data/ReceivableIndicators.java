package com.fksoft.erp.domain.financial.service.data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimum Financial Operations indicators (read model) over the Receivables visible to the caller — an
 * operational received-payments &amp; collection view. Two scopes, mirroring the other indicator views:
 *
 * <ul>
 *   <li><b>Current snapshot</b> (independent of the period): {@code openCount}, {@code partiallyPaidCount},
 *       {@code overdueCount} and {@code outstandingAmount} (the amount still to receive over the visible
 *       non-cancelled Receivables);
 *   <li><b>Volume in the selected period</b> (by <b>payment date</b>): {@code paidReceivablesInPeriod} (the
 *       Receivables settled in the period), {@code paymentsRegistered} and {@code receivedAmount} (the effective,
 *       non-reversed payments received in the period) and {@code paymentsByMethod} (those payments grouped by
 *       payment method).
 * </ul>
 *
 * This is <b>operational, not executive reporting</b>: it exposes receivable + received-payment figures only —
 * <b>never</b> Commission, Accounts Payable, bank-reconciliation, accounting-ledger, fiscal or cash-flow data.
 *
 * @param openCount current number of {@code OPEN} Receivables visible to the caller
 * @param partiallyPaidCount current number of {@code PARTIALLY_PAID} Receivables
 * @param overdueCount current number of {@code OVERDUE} Receivables
 * @param outstandingAmount the current outstanding total (Σ total − amountPaid over non-cancelled receivables)
 * @param paidReceivablesInPeriod the Receivables settled ({@code PAID}) with their last payment in the period
 * @param paymentsRegistered the count of non-reversed payments received in the period
 * @param receivedAmount the sum of the non-reversed payments received in the period
 * @param paymentsByMethod the received payments in the period grouped by payment method
 */
public record ReceivableIndicators(
        long openCount,
        long partiallyPaidCount,
        long overdueCount,
        BigDecimal outstandingAmount,
        long paidReceivablesInPeriod,
        long paymentsRegistered,
        BigDecimal receivedAmount,
        List<MethodTotal> paymentsByMethod) {

    /** Received total + count for one payment method over the period (non-reversed payments). */
    public record MethodTotal(String method, String methodLabel, long count, BigDecimal amount) {}
}
