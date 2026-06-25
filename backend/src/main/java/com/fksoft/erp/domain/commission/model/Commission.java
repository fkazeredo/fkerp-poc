package com.fksoft.erp.domain.commission.model;

import com.fksoft.erp.domain.sales.model.CommercialOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An Expected Commission (Commission Management): the forecast of the commission a commercial actor will earn on a
 * closed Commercial Order, generated so the future commission payment is tracked from the start. It is a
 * <b>forecast</b> ({@code EXPECTED}, not payable yet): the amount is a percentage (snapshot of the applied
 * {@link CommissionRule}) of a base — the Order's commercial total, or the amount already received on the Order's
 * Receivable when that is available ({@link CommissionBasis}). It <b>preserves</b> the commercial origin (source
 * Order / Proposal / Opportunity / Lead), the beneficiary and the rule used. Commission Management does NOT own the
 * Commercial Order or the Receivable (it only reads them), and generating a Commission creates NO Commission Payment,
 * Accounts Payable, payroll, tax or accounting data.
 */
@Entity
@Table(name = "commissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Commission {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The source Commercial Order this commission was generated from (preserved; the Order is not modified).
    @NotNull
    @Column(name = "commercial_order_id", nullable = false, updatable = false)
    private UUID commercialOrderId;

    // The commercial origin chain, kept for traceability (snapshotted from the Order).
    @NotNull
    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @NotNull
    @Column(name = "opportunity_id", nullable = false, updatable = false)
    private UUID opportunityId;

    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    // The commercial actor who earns the commission (= the Order's commercial responsible at generation time).
    @NotNull
    @Column(name = "beneficiary_user_id", nullable = false, updatable = false)
    private UUID beneficiaryUserId;

    // The Commission Rule applied, plus its percentage snapshot (kept even if the rule later changes).
    @NotNull
    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @NotNull
    @Column(name = "rule_percentage", nullable = false)
    private BigDecimal rulePercentage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "basis_type", nullable = false, length = 30)
    private CommissionBasis basisType;

    // The base the amount was calculated from (commercial total or received amount), and the resulting amount.
    @NotNull
    @PositiveOrZero
    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommissionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    /**
     * Generates a new {@code EXPECTED} Commission from a closed Commercial Order, snapshotting the commercial origin,
     * the beneficiary (the Order's commercial responsible), the applied rule and its percentage, and computing the
     * amount as {@code baseAmount × rulePercentage ÷ 100} (scale 2, {@code HALF_UP}). The basis (commercial vs
     * received) and the base amount are decided by the Application Service (which reads the Receivable). Creates no
     * Commission Payment / Accounts Payable / payroll / tax / accounting data.
     *
     * @param order the source Commercial Order (commercially closed, with a responsible and a positive total)
     * @param rule the applied Commission Rule
     * @param basis the basis the amount was calculated from
     * @param baseAmount the base amount (the commercial total or the received amount)
     * @param createdBy id of the user generating the commission
     * @return a new, unsaved, {@code EXPECTED} commission
     */
    public static Commission generate(
            CommercialOrder order, CommissionRule rule, CommissionBasis basis, BigDecimal baseAmount, UUID createdBy) {
        Commission commission = new Commission();
        commission.id = UUID.randomUUID();
        commission.commercialOrderId = order.id();
        commission.proposalId = order.proposalId();
        commission.opportunityId = order.opportunityId();
        commission.leadId = order.leadId();
        commission.beneficiaryUserId = order.responsiblePersonId();
        commission.ruleId = rule.id();
        commission.rulePercentage = rule.percentage().setScale(SCALE, RoundingMode.HALF_UP);
        commission.basisType = basis;
        commission.baseAmount = baseAmount.setScale(SCALE, RoundingMode.HALF_UP);
        commission.amount =
                commission.baseAmount.multiply(commission.rulePercentage).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        commission.status = CommissionStatus.EXPECTED;
        commission.createdBy = createdBy;
        commission.updatedBy = createdBy;
        return commission;
    }
}
