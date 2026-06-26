package com.fksoft.erp.domain.commission.service.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Simple commission statement for one beneficiary over an (optional) period: the commission entries plus the per-status
 * totals (expected / eligible / approved / paid). Informational only — it approves/pays nothing and carries commission
 * + commercial-origin data only (never payroll, tax or accounting data). Visibility is enforced by the service (a
 * seller/representative sees only their own statement; a manager/finance can see any beneficiary).
 *
 * @param beneficiaryId the beneficiary user id
 * @param beneficiaryName the resolved beneficiary name, or {@code null}
 * @param periodFrom the inclusive period start (by creation date), or {@code null} (all time)
 * @param periodTo the inclusive period end (by creation date), or {@code null} (all time)
 * @param entries the commission entries (newest first)
 * @param totals the per-status totals
 */
public record CommissionStatement(
        java.util.UUID beneficiaryId,
        String beneficiaryName,
        LocalDate periodFrom,
        LocalDate periodTo,
        List<CommissionListItem> entries,
        Totals totals) {

    /**
     * The per-status totals over the statement's entries — amount sums and counts for the non-voided lifecycle.
     *
     * @param totalExpected the sum of EXPECTED amounts
     * @param totalEligible the sum of ELIGIBLE amounts
     * @param totalApproved the sum of APPROVED amounts
     * @param totalPaid the sum of PAID amounts
     * @param countExpected the number of EXPECTED entries
     * @param countEligible the number of ELIGIBLE entries
     * @param countApproved the number of APPROVED entries
     * @param countPaid the number of PAID entries
     */
    public record Totals(
            BigDecimal totalExpected,
            BigDecimal totalEligible,
            BigDecimal totalApproved,
            BigDecimal totalPaid,
            long countExpected,
            long countEligible,
            long countApproved,
            long countPaid) {}

    /**
     * Builds a statement, computing the per-status totals from the entries in memory (a per-beneficiary statement is a
     * small result set).
     *
     * @param beneficiaryId the beneficiary user id
     * @param beneficiaryName the resolved beneficiary name, or {@code null}
     * @param from the inclusive period start, or {@code null}
     * @param to the inclusive period end, or {@code null}
     * @param entries the commission entries
     * @return the statement with computed totals
     */
    public static CommissionStatement of(
            java.util.UUID beneficiaryId,
            String beneficiaryName,
            LocalDate from,
            LocalDate to,
            List<CommissionListItem> entries) {
        Totals totals = new Totals(
                sum(entries, "EXPECTED"),
                sum(entries, "ELIGIBLE"),
                sum(entries, "APPROVED"),
                sum(entries, "PAID"),
                count(entries, "EXPECTED"),
                count(entries, "ELIGIBLE"),
                count(entries, "APPROVED"),
                count(entries, "PAID"));
        return new CommissionStatement(beneficiaryId, beneficiaryName, from, to, entries, totals);
    }

    private static BigDecimal sum(List<CommissionListItem> entries, String status) {
        return entries.stream()
                .filter(e -> status.equals(e.status()))
                .map(CommissionListItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long count(List<CommissionListItem> entries, String status) {
        return entries.stream().filter(e -> status.equals(e.status())).count();
    }
}
