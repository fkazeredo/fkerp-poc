package com.fksoft.erp.domain.commission.service.data;

import com.fksoft.erp.domain.commission.model.Commission;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full read view of a Commission for the detail consultation. Keeps the commercial origin (Order / Proposal /
 * Opportunity / Lead) and the related Receivable traceable, exposes the calculation basis and the applied rule
 * (the {@code rulePercentage} is the immutable snapshot, so the rule used stays visible even if the rule changes),
 * the amount/status, and the lifecycle stamps (eligibility / approval / payment) shown when available. Carries
 * commission + commercial-origin data only — never payroll, tax or accounting data.
 *
 * @param id the commission id
 * @param commercialOrderId the source Commercial Order id
 * @param orderNumber the human-friendly source order number (PC-000n)
 * @param proposalId the source Proposal id
 * @param proposalReference the source Proposal title, or {@code null}
 * @param opportunityId the source Opportunity id
 * @param opportunityReference the source Opportunity name, or {@code null}
 * @param leadId the source Lead id
 * @param beneficiaryUserId the commercial actor who earns the commission
 * @param beneficiaryName the resolved beneficiary name, or {@code null}
 * @param ruleId the applied Commission Rule id
 * @param ruleName the resolved rule name, or {@code null}
 * @param rulePercentage the applied percentage (snapshot)
 * @param basisType the basis the amount was calculated from ({@code COMMERCIAL_AMOUNT}/{@code RECEIVED_AMOUNT})
 * @param baseAmount the base amount (commercial total or received amount)
 * @param amount the calculated commission amount
 * @param status the commission status
 * @param receivableId the source order's active Receivable id, or {@code null} when there is none
 * @param receivableStatus the source order's active Receivable status, or {@code null}
 * @param eligibleAt when it became eligible (its receivable was paid), or {@code null}
 * @param approvedAt when it was approved, or {@code null} (a later slice)
 * @param paidAt when its commission payment was registered, or {@code null} (a later slice)
 * @param createdByName who generated the commission, or {@code null}
 * @param createdAt when the commission was generated
 */
public record CommissionDetail(
        UUID id,
        UUID commercialOrderId,
        long orderNumber,
        UUID proposalId,
        String proposalReference,
        UUID opportunityId,
        String opportunityReference,
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
        UUID receivableId,
        String receivableStatus,
        Instant eligibleAt,
        Instant approvedAt,
        Instant paidAt,
        String createdByName,
        Instant createdAt) {

    /**
     * The resolved cross-aggregate values for the detail (bundled to keep the factory within the parameter limit).
     *
     * @param beneficiaryName the resolved beneficiary name, or {@code null}
     * @param ruleName the resolved rule name, or {@code null}
     * @param proposalReference the resolved Proposal title, or {@code null}
     * @param opportunityReference the resolved Opportunity name, or {@code null}
     * @param receivableId the source order's active Receivable id, or {@code null}
     * @param receivableStatus the source order's active Receivable status, or {@code null}
     * @param createdByName who generated the commission, or {@code null}
     */
    public record Refs(
            String beneficiaryName,
            String ruleName,
            String proposalReference,
            String opportunityReference,
            UUID receivableId,
            String receivableStatus,
            String createdByName) {}

    /**
     * Builds the detail from the entity, the resolved source order number and the resolved cross-aggregate
     * references.
     *
     * @param c the commission entity
     * @param orderNumber the resolved source order number
     * @param refs the resolved cross-aggregate references
     * @return the read view
     */
    public static CommissionDetail from(Commission c, long orderNumber, Refs refs) {
        return new CommissionDetail(
                c.id(),
                c.commercialOrderId(),
                orderNumber,
                c.proposalId(),
                refs.proposalReference(),
                c.opportunityId(),
                refs.opportunityReference(),
                c.leadId(),
                c.beneficiaryUserId(),
                refs.beneficiaryName(),
                c.ruleId(),
                refs.ruleName(),
                c.rulePercentage(),
                c.basisType().name(),
                c.baseAmount(),
                c.amount(),
                c.status().name(),
                refs.receivableId(),
                refs.receivableStatus(),
                c.eligibleAt(),
                c.approvedAt(),
                c.paidAt(),
                refs.createdByName(),
                c.createdAt());
    }
}
