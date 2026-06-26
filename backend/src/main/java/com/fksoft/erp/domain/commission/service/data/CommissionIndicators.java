package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.service.data.CommissionOperationalSummary.BeneficiaryTotal;
import com.fksoft.erp.domain.commission.service.data.CommissionOperationalSummary.StatusTotal;
import java.math.BigDecimal;
import java.util.List;

/**
 * Minimum functional Commission Management indicators (read model) over the commissions visible to the caller — a
 * manager's minimal view of commission obligations and payments. Two scopes, mirroring the other indicator views:
 *
 * <ul>
 *   <li><b>Current snapshot</b> (independent of the period): {@code byStatus} (count + total amount per status) and
 *       {@code byBeneficiary} (count + total amount per beneficiary); the amount + count <b>pending approval</b>
 *       ({@code ELIGIBLE}) and <b>pending payment</b> ({@code APPROVED}).
 *   <li><b>Volume in the selected period</b>: the commissions <b>paid</b> in the period ({@code paidInPeriodCount},
 *       {@code paidInPeriodAmount}, by the human payment date, inclusive).
 * </ul>
 *
 * The two latency averages — from <b>eligibility to approval</b> and from <b>approval to payment</b> (in whole
 * seconds; {@code null} when none has crossed that step yet) — are snapshot health metrics over all the visible
 * commissions that crossed the step, independent of the selected period.
 *
 * This is <b>operational, not executive reporting</b>: it exposes commission figures only — <b>never</b> payroll, tax,
 * accounting, accounts-payable or bank-reconciliation data.
 *
 * @param byStatus the current count + total amount of commissions per status (a snapshot)
 * @param byBeneficiary the current count + total amount of commissions per beneficiary (a snapshot)
 * @param pendingApprovalCount the current number of {@code ELIGIBLE} commissions (pending approval)
 * @param pendingApprovalAmount the current total amount of the {@code ELIGIBLE} commissions
 * @param pendingPaymentCount the current number of {@code APPROVED} commissions (pending payment)
 * @param pendingPaymentAmount the current total amount of the {@code APPROVED} commissions
 * @param paidInPeriodCount the number of commissions paid in the period (by the human payment date, inclusive)
 * @param paidInPeriodAmount the total amount paid in the period
 * @param avgEligibilityToApprovalSeconds the average seconds from eligibility to approval over all visible
 *     commissions that have been approved, or {@code null} when none
 * @param avgApprovalToPaymentSeconds the average seconds from approval to payment over all visible commissions that
 *     have been paid, or {@code null} when none
 */
public record CommissionIndicators(
        List<StatusTotal> byStatus,
        List<BeneficiaryTotal> byBeneficiary,
        long pendingApprovalCount,
        BigDecimal pendingApprovalAmount,
        long pendingPaymentCount,
        BigDecimal pendingPaymentAmount,
        long paidInPeriodCount,
        BigDecimal paidInPeriodAmount,
        Long avgEligibilityToApprovalSeconds,
        Long avgApprovalToPaymentSeconds) {}
