package com.fksoft.erp.domain.sales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A commercial-offer line of a {@link CommercialOrder}: an immutable snapshot of the source
 * {@link ProposalItem} taken when the Order is created (type, description, quantity, unit value and optional
 * discount). The Order preserves exactly what was sold; the line total is computed the same way as the
 * Proposal item ({@code unitValue * quantity - discount}).
 */
@Entity
@Table(name = "commercial_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialOrderItem {

    private static final int SCALE = 2;

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
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

    /** Snapshots a source Proposal item into a new Order item (a copy — the Proposal item is untouched). */
    static CommercialOrderItem snapshotOf(ProposalItem source) {
        CommercialOrderItem item = new CommercialOrderItem();
        item.id = UUID.randomUUID();
        item.type = source.type();
        item.description = source.description();
        item.quantity = source.quantity();
        item.unitValue = source.unitValue();
        item.discountType = source.discountType();
        item.discountValue = source.discountValue();
        return item;
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

    /** Whether this item requires a booking operation (per the item type's cadastro classification). */
    public boolean requiresBooking() {
        return type.requiresBooking();
    }
}
