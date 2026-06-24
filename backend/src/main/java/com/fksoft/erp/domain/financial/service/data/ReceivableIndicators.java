package com.fksoft.erp.domain.financial.service.data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimum functional Financial Operations indicators (read model) over the Receivables visible to the caller —
 * a manager's minimal view of receivables and received payments. Two scopes, mirroring the other indicator views:
 *
 * <ul>
 *   <li><b>Volume in the selected period</b>: receivables <b>created</b> in the period ({@code
 *       totalReceivablesInPeriod}, {@code totalToReceive}); the effective (non-reversed) payments <b>received</b> in
 *       the period by payment date ({@code receivedAmount}, {@code paymentsRegistered}, {@code paymentsByMethod});
 *       and the receivables <b>settled</b> in the period ({@code paidReceivablesInPeriod}, {@code avgDaysToPayment}).
 *   <li><b>Current snapshot</b> (independent of the period): {@code byStatus} (counts per status),
 *       {@code outstandingAmount} (still to receive over the non-cancelled receivables), {@code overdueAmount} (still
 *       to receive over the overdue receivables) and {@code readyForCommission} (the PAID receivables — identifiable
 *       as ready for Commission Management in Sprint 6).
 * </ul>
 *
 * This is <b>operational, not executive reporting</b>: it exposes receivable + received-payment figures only —
 * <b>never</b> Commission calculation/forecast, Accounts Payable, bank reconciliation, accounting-ledger, fiscal,
 * P&amp;L or cash-flow data.
 *
 * @param totalReceivablesInPeriod the number of receivables created in the period
 * @param totalToReceive the gross value (Σ total) of the receivables created in the period
 * @param receivedAmount the sum of the non-reversed payments received in the period (by payment date)
 * @param paymentsRegistered the count of non-reversed payments received in the period
 * @param paymentsByMethod the received payments in the period grouped by payment method
 * @param paidReceivablesInPeriod the receivables settled ({@code PAID}) with their last payment in the period
 * @param avgDaysToPayment the average days from creation to settlement over the receivables settled in the period,
 *     or {@code null} when none were settled in the period
 * @param byStatus the current count of receivables per status (a snapshot)
 * @param outstandingAmount the current amount still to receive (Σ total − amountPaid over non-cancelled receivables)
 * @param overdueAmount the current amount still to receive over the overdue receivables
 * @param readyForCommission the current number of {@code PAID} receivables (ready for Commission Management)
 */
public record ReceivableIndicators(
        long totalReceivablesInPeriod,
        BigDecimal totalToReceive,
        BigDecimal receivedAmount,
        long paymentsRegistered,
        List<MethodTotal> paymentsByMethod,
        long paidReceivablesInPeriod,
        Long avgDaysToPayment,
        List<StatusCount> byStatus,
        BigDecimal outstandingAmount,
        BigDecimal overdueAmount,
        long readyForCommission) {

    /** Received total + count for one payment method over the period (non-reversed payments). */
    public record MethodTotal(String method, String methodLabel, long count, BigDecimal amount) {}

    /** Current receivable count for a lifecycle status code. */
    public record StatusCount(String status, long count) {}
}
