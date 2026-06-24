package com.fksoft.erp.domain.financial.model;

import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.PaymentExceedsOutstandingException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
 * A single installment of a {@link Receivable} (part of the Receivable aggregate): a slice of the amount due,
 * with its own due date, paid amount and status. The installments of a Receivable always sum to its total. It is
 * NOT a Commission or Invoice record; scheduling it registers none of those. It starts {@code OPEN} and, as
 * payments are applied, moves to {@code PARTIALLY_PAID} (0 &lt; paid &lt; amount) then {@code PAID} (paid ==
 * amount).
 */
@Entity
@Table(name = "receivable_installments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivableInstallment {

    private static final int SCALE = 2;

    @Id
    private UUID id;

    // The 1-based position of this installment within the Receivable's schedule.
    @Min(1)
    @Column(name = "number", nullable = false)
    private int number;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal amount;

    // The amount already received against this installment (denormalized; sums the installment's payments). It
    // drives the installment status: 0 → OPEN, 0 < paid < amount → PARTIALLY_PAID, paid == amount → PAID.
    @NotNull
    @PositiveOrZero
    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status;

    @Size(max = 2000)
    @Column(name = "payment_notes")
    private String paymentNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Builds an installment, validating it (amount required and non-negative, due date required) as a
     * last-resort domain guard. It starts {@code OPEN}.
     *
     * @param number the 1-based position in the schedule
     * @param amount the installment amount (required, non-negative; normalized to scale 2)
     * @param dueDate the installment due date (required)
     * @param paymentNotes optional descriptive notes (free text — not a Payment record)
     * @return a new, unsaved installment
     * @throws InstallmentScheduleInvalidException if the amount is null/negative or the due date is null
     */
    static ReceivableInstallment of(int number, BigDecimal amount, LocalDate dueDate, String paymentNotes) {
        if (amount == null || amount.signum() < 0 || dueDate == null) {
            throw new InstallmentScheduleInvalidException();
        }
        ReceivableInstallment installment = new ReceivableInstallment();
        installment.id = UUID.randomUUID();
        installment.number = number;
        installment.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        installment.amountPaid = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        installment.dueDate = dueDate;
        installment.paymentNotes = emptyToNull(paymentNotes);
        installment.status = InstallmentStatus.OPEN;
        return installment;
    }

    /** The amount still due on this installment ({@code amount − amountPaid}). */
    public BigDecimal outstanding() {
        return amount.subtract(amountPaid);
    }

    /**
     * Whether this installment is past its due date with a balance remaining — i.e. it still requires payment
     * ({@code OPEN}/{@code PARTIALLY_PAID}) and its due date is before the given reference date.
     *
     * @param today the reference (current) date
     * @return {@code true} if the installment is unpaid/partially paid and past due
     */
    public boolean isPastDue(LocalDate today) {
        return (status == InstallmentStatus.OPEN || status == InstallmentStatus.PARTIALLY_PAID)
                && dueDate.isBefore(today);
    }

    /**
     * Applies a payment of {@code paymentAmount} against this installment: increases the paid amount and moves
     * the status to {@code PAID} when fully covered, otherwise {@code PARTIALLY_PAID}. A payment may settle the
     * installment fully (amount == outstanding) or partially. It may NOT exceed the outstanding amount
     * (overpayment is out of scope), and an already {@code PAID}/{@code CANCELLED} installment is not payable. The
     * payment amount must be positive — enforced at the request boundary ({@code @Positive} → 400).
     *
     * @param paymentAmount the amount received (must be ≤ {@link #outstanding()})
     * @throws InstallmentNotPayableException if the installment is already {@code PAID} or {@code CANCELLED}
     * @throws PaymentExceedsOutstandingException if the amount exceeds the outstanding amount
     */
    void applyPayment(BigDecimal paymentAmount) {
        if (status == InstallmentStatus.PAID || status == InstallmentStatus.CANCELLED) {
            throw new InstallmentNotPayableException();
        }
        if (paymentAmount.compareTo(outstanding()) > 0) {
            throw new PaymentExceedsOutstandingException();
        }
        amountPaid = amountPaid.add(paymentAmount).setScale(SCALE, RoundingMode.HALF_UP);
        status = amountPaid.compareTo(amount) >= 0 ? InstallmentStatus.PAID : InstallmentStatus.PARTIALLY_PAID;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
