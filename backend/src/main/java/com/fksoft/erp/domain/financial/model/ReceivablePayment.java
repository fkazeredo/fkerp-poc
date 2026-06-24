package com.fksoft.erp.domain.financial.model;

import com.fksoft.erp.domain.financial.exception.PaymentAlreadyReversedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A payment registered against a single {@link ReceivableInstallment} (part of the {@link Receivable}
 * aggregate): the record that an amount was received for an installment, with the payment date, the payment
 * method (a managed cadastro) and who/when registered it. This slice registers full payments only (the amount
 * equals the installment amount). It is an append-only history entry — it is NOT a Commission, Invoice or
 * bank-reconciliation record, and registering it issues no invoice and triggers no Customer Care.
 */
@Entity
@Table(name = "receivable_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivablePayment {

    private static final int SCALE = 2;

    @Id
    private UUID id;

    // The installment this payment settles (within the same Receivable aggregate).
    @NotNull
    @Column(name = "installment_id", nullable = false, updatable = false)
    private UUID installmentId;

    @NotNull
    @Positive
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @NotNull
    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    // How the payment was received — a managed cadastro (reference data, never an enum).
    @NotNull
    @ManyToOne
    @JoinColumn(name = "payment_method_id", nullable = false, updatable = false)
    private PaymentMethod method;

    @Size(max = 2000)
    @Column(updatable = false)
    private String note;

    @NotNull
    @Column(name = "registered_by", nullable = false, updatable = false)
    private UUID registeredBy;

    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    // Reversal state (a payment-entry correction): null = the payment stands; set = reversed (kept in history,
    // never deleted). Unlike the immutable creation fields, these are updatable (stamped once, by reverse()).
    @Size(max = 2000)
    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    /**
     * Builds a payment for an installment (the amount is normalized to scale 2). The amount / payable-state
     * checks live on {@link Receivable#registerPayment}.
     *
     * @param installmentId the settled installment id
     * @param amount the payment amount (positive)
     * @param paymentDate the date the payment was received
     * @param method the payment method (an active cadastro value)
     * @param note optional free-text reference/note
     * @param registeredBy the user registering the payment
     * @return a new, unsaved payment
     */
    static ReceivablePayment of(
            UUID installmentId,
            BigDecimal amount,
            LocalDate paymentDate,
            PaymentMethod method,
            String note,
            UUID registeredBy) {
        ReceivablePayment payment = new ReceivablePayment();
        payment.id = UUID.randomUUID();
        payment.installmentId = installmentId;
        payment.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        payment.paymentDate = paymentDate;
        payment.method = method;
        payment.note = emptyToNull(note);
        payment.registeredBy = registeredBy;
        return payment;
    }

    /**
     * Marks this payment reversed (a payment-entry correction): records the reason and who/when, keeping the
     * payment in history (it is never deleted). The amount/standing re-derivation is owned by
     * {@link Receivable#reversePayment}.
     *
     * @param reason the reason for the reversal (required at the request boundary)
     * @param reversedBy the user reversing the payment
     * @param reversedAt when the reversal happened
     * @throws PaymentAlreadyReversedException if the payment has already been reversed
     */
    void reverse(String reason, UUID reversedBy, Instant reversedAt) {
        if (reversed()) {
            throw new PaymentAlreadyReversedException();
        }
        this.reversalReason = emptyToNull(reason);
        this.reversedBy = reversedBy;
        this.reversedAt = reversedAt;
    }

    /** Whether this payment has been reversed (no longer counts towards the paid amount). */
    public boolean reversed() {
        return reversedAt != null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
