package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.Commission;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of an Expected Commission. Carries commission + commercial-origin data only — never Commission Payment,
 * Accounts Payable, payroll, tax or accounting data.
 *
 * @param id the commission id
 * @param commercialOrderId the source Commercial Order id
 * @param orderNumber the human-friendly order number (PC-000n)
 * @param proposalId the source Proposal id
 * @param opportunityId the source Opportunity id
 * @param leadId the source Lead id
 * @param beneficiaryUserId the commercial actor who earns the commission
 * @param beneficiaryName the resolved beneficiary name, or {@code null}
 * @param ruleId the applied Commission Rule id
 * @param ruleName the resolved rule name, or {@code null}
 * @param rulePercentage the applied percentage (snapshot)
 * @param basisType the basis the amount was calculated from ({@code COMMERCIAL_AMOUNT}/{@code RECEIVED_AMOUNT})
 * @param baseAmount the base amount (commercial total or received amount)
 * @param amount the calculated commission amount
 * @param status the commission status ({@code EXPECTED} or {@code ELIGIBLE} in this slice)
 * @param eligibleAt when the commission became eligible (the related Receivable was paid), or {@code null}
 * @param createdAt when the commission was generated
 */
public record CommissionDetail(
        UUID id,
        UUID commercialOrderId,
        long orderNumber,
        UUID proposalId,
        UUID opportunityId,
        UUID leadId,
        UUID beneficiaryUserId,
        String beneficiaryName,
        UUID ruleId,
        String ruleName,
        BigDecimal rulePercentage,
        String basisType,
        BigDecimal baseAmount,
        BigDecimal amount,
        String status,
        Instant eligibleAt,
        Instant createdAt) {

    /**
     * Builds the detail from the entity and the resolved order number, beneficiary and rule names.
     *
     * @param commission the commission entity
     * @param orderNumber the source order number
     * @param beneficiaryName the resolved beneficiary name, or {@code null}
     * @param ruleName the resolved rule name, or {@code null}
     * @return the read view
     */
    public static CommissionDetail from(
            Commission commission, long orderNumber, String beneficiaryName, String ruleName) {
        return new CommissionDetail(
                commission.id(),
                commission.commercialOrderId(),
                orderNumber,
                commission.proposalId(),
                commission.opportunityId(),
                commission.leadId(),
                commission.beneficiaryUserId(),
                beneficiaryName,
                commission.ruleId(),
                ruleName,
                commission.rulePercentage(),
                commission.basisType().name(),
                commission.baseAmount(),
                commission.amount(),
                commission.status().name(),
                commission.eligibleAt(),
                commission.createdAt());
    }
}
