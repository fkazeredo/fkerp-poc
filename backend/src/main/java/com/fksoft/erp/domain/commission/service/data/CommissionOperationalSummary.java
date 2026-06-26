package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Operational grouping of the commissions visible to the caller (and matching the active filters): the count + total
 * amount <b>by status</b> and <b>by beneficiary</b>, plus the overall count + total. It is an operational view — not
 * Executive Reporting — and carries commission figures only (never payroll, tax, accounting or accounts-payable data).
 * Visibility is enforced by the service before the grouping, so a seller/representative only ever groups their own
 * commissions.
 *
 * @param totalCount the number of commissions in the grouped set
 * @param totalAmount the sum of their amounts
 * @param byStatus the per-status totals (only the statuses present, in lifecycle order)
 * @param byBeneficiary the per-beneficiary totals (ordered by beneficiary name)
 */
public record CommissionOperationalSummary(
        long totalCount, BigDecimal totalAmount, List<StatusTotal> byStatus, List<BeneficiaryTotal> byBeneficiary) {

    /**
     * Per-status total of the grouped commissions.
     *
     * @param status the commission status
     * @param count the number of commissions with that status
     * @param totalAmount the sum of their amounts
     */
    public record StatusTotal(CommissionStatus status, long count, BigDecimal totalAmount) {}

    /**
     * Per-beneficiary total of the grouped commissions.
     *
     * @param beneficiaryUserId the beneficiary user id
     * @param beneficiaryName the resolved beneficiary name, or {@code null}
     * @param count the number of commissions for that beneficiary
     * @param totalAmount the sum of their amounts
     */
    public record BeneficiaryTotal(
            UUID beneficiaryUserId, String beneficiaryName, long count, BigDecimal totalAmount) {}

    /**
     * Groups the given commissions by status and by beneficiary in memory (the operational set a manager works is
     * small — Rule Zero, no DB aggregation). Statuses are emitted in lifecycle order, skipping the absent ones;
     * beneficiaries are ordered by name.
     *
     * @param rows the commissions the caller may see (already filtered)
     * @param beneficiaryNames the resolved beneficiary id → name map
     * @return the operational summary
     */
    public static CommissionOperationalSummary of(List<Commission> rows, Map<UUID, String> beneficiaryNames) {
        List<StatusTotal> byStatus = Arrays.stream(CommissionStatus.values())
                .map(status -> {
                    List<Commission> group =
                            rows.stream().filter(c -> c.status() == status).toList();
                    return group.isEmpty() ? null : new StatusTotal(status, group.size(), sum(group));
                })
                .filter(Objects::nonNull)
                .toList();
        List<BeneficiaryTotal> byBeneficiary =
                rows.stream().collect(Collectors.groupingBy(Commission::beneficiaryUserId)).entrySet().stream()
                        .map(e -> new BeneficiaryTotal(
                                e.getKey(),
                                beneficiaryNames.get(e.getKey()),
                                e.getValue().size(),
                                sum(e.getValue())))
                        .sorted(Comparator.comparing(
                                b -> b.beneficiaryName() == null ? "" : b.beneficiaryName(),
                                String.CASE_INSENSITIVE_ORDER))
                        .toList();
        return new CommissionOperationalSummary(rows.size(), sum(rows), byStatus, byBeneficiary);
    }

    private static BigDecimal sum(List<Commission> rows) {
        return rows.stream().map(Commission::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
