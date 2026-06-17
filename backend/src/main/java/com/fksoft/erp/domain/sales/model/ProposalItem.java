package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A line of a commercial {@link Proposal} (part of the Proposal aggregate): what the company intends to
 * sell. Carries a type, a description, a quantity, a unit value and an optional discount (an absolute
 * amount or a percentage, per the negotiation). It contributes its {@link #lineTotal()} to the Proposal
 * total. It does NOT create a Booking, check external availability, or compute supplier cost / margin /
 * tax. Items are managed only while the Proposal is a Draft.
 */
@Entity
@Table(name = "proposal_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalItem {

    private static final int SCALE = 2;

    @Id
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalItemType type;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false)
    private String description;

    @Min(1)
    @Column(nullable = false)
    private int quantity;

    @NotNull
    @PositiveOrZero
    @Column(name = "unit_value", nullable = false)
    private BigDecimal unitValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type")
    private DiscountType discountType;

    @PositiveOrZero
    @Column(name = "discount_value")
    private BigDecimal discountValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static ProposalItem of(
            ProposalItemType type,
            String description,
            int quantity,
            BigDecimal unitValue,
            DiscountType discountType,
            BigDecimal discountValue) {
        validateDiscount(quantity, unitValue, discountType, discountValue);
        ProposalItem item = new ProposalItem();
        item.id = UUID.randomUUID();
        item.type = type;
        item.description = description;
        item.quantity = quantity;
        item.unitValue = unitValue;
        item.discountType = discountType;
        item.discountValue = discountValue;
        return item;
    }

    void update(
            ProposalItemType type,
            String description,
            int quantity,
            BigDecimal unitValue,
            DiscountType discountType,
            BigDecimal discountValue) {
        validateDiscount(quantity, unitValue, discountType, discountValue);
        this.type = type;
        this.description = description;
        this.quantity = quantity;
        this.unitValue = unitValue;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /** The line subtotal before any discount ({@code unitValue * quantity}). */
    public BigDecimal subtotal() {
        return unitValue.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** The discount amount applied to this line (zero when there is no discount). */
    public BigDecimal discountAmount() {
        if (discountType == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return discountType.amountOf(discountValue, subtotal());
    }

    /** The line total: the subtotal minus the discount. */
    public BigDecimal lineTotal() {
        return subtotal().subtract(discountAmount()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Validates the optional discount: type and value are present together or both absent; a percentage is
     * 0–100; an absolute amount is between 0 and the line subtotal. The range rules live on
     * {@link DiscountType#isValid(BigDecimal, BigDecimal)} so the item and the Proposal share them.
     */
    private static void validateDiscount(
            int quantity, BigDecimal unitValue, DiscountType discountType, BigDecimal discountValue) {
        if ((discountType == null) != (discountValue == null)) {
            throw new ProposalItemInvalidException();
        }
        if (discountType == null) {
            return;
        }
        BigDecimal subtotal = unitValue.multiply(BigDecimal.valueOf(quantity));
        if (!discountType.isValid(discountValue, subtotal)) {
            throw new ProposalItemInvalidException();
        }
    }
}
