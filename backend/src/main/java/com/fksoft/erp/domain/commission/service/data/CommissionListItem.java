package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.Commission;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational list item of a Commission — the information a commercial/financial manager needs to track commissions
 * (expected / eligible / approved / paid). Carries commission + commercial-origin data only — never payroll, tax,
 * accounting or generic accounts-payable data. Built from the entity via {@link #from} with the resolved
 * cross-aggregate names and the source order's Receivable status.
 *
 * @param id the commission id
 * @param beneficiaryUserId the commercial actor who earns the commission
 * @param beneficiaryName the resolved beneficiary name, or {@code null}
 * @param commercialOrderId the source Commercial Order id
 * @param orderNumber the human-friendly source order number (PC-000n)
 * @param proposalReference the source Proposal title, or {@code null}
 * @param opportunityReference the source Opportunity name, or {@code null}
 * @param amount the calculated commission amount
 * @param baseAmount the base the amount was calculated from
 * @param basisType the basis ({@code COMMERCIAL_AMOUNT}/{@code RECEIVED_AMOUNT})
 * @param rulePercentage the applied percentage (snapshot)
 * @param ruleName the applied rule name, or {@code null}
 * @param status the commission status
 * @param receivableStatus the source order's active Receivable status, or {@code null} when there is none
 * @param createdAt when the commission was generated
 * @param eligibleAt when it became eligible (its receivable was paid), or {@code null}
 * @param approvedAt when it was approved, or {@code null} (a later slice)
 * @param paidAt when its commission payment was registered, or {@code null} (a later slice)
 */
public record CommissionListItem(
        UUID id,
        UUID beneficiaryUserId,
        String beneficiaryName,
        UUID commercialOrderId,
        long orderNumber,
        String proposalReference,
        String opportunityReference,
        BigDecimal amount,
        BigDecimal baseAmount,
        String basisType,
        BigDecimal rulePercentage,
        String ruleName,
        String status,
        String receivableStatus,
        Instant createdAt,
        Instant eligibleAt,
        Instant approvedAt,
        Instant paidAt) {

    /**
     * Builds the list item from the entity and the resolved cross-aggregate data.
     *
     * @param c the commission entity
     * @param orderNumber the resolved source order number
     * @param beneficiaryName the resolved beneficiary name, or {@code null}
     * @param ruleName the resolved rule name, or {@code null}
     * @param proposalReference the resolved Proposal title, or {@code null}
     * @param opportunityReference the resolved Opportunity name, or {@code null}
     * @param receivableStatus the source order's active Receivable status, or {@code null}
     * @return the list item
     */
    public static CommissionListItem from(
            Commission c,
            long orderNumber,
            String beneficiaryName,
            String ruleName,
            String proposalReference,
            String opportunityReference,
            String receivableStatus) {
        return new CommissionListItem(
                c.id(),
                c.beneficiaryUserId(),
                beneficiaryName,
                c.commercialOrderId(),
                orderNumber,
                proposalReference,
                opportunityReference,
                c.amount(),
                c.baseAmount(),
                c.basisType().name(),
                c.rulePercentage(),
                ruleName,
                c.status().name(),
                receivableStatus,
                c.createdAt(),
                c.eligibleAt(),
                c.approvedAt(),
                c.paidAt());
    }
}
