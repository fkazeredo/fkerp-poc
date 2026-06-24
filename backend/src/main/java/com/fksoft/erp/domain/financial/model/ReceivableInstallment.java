package com.fksoft.erp.domain.financial.model;

import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
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
 * with its own due date and status. The installments of a Receivable always sum to its total. It is NOT a
 * Payment, Commission or Invoice record; scheduling it registers none of those. It starts {@code OPEN};
 * the transitions beyond {@code OPEN} are driven by payment behavior (a later slice).
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
        installment.dueDate = dueDate;
        installment.paymentNotes = emptyToNull(paymentNotes);
        installment.status = InstallmentStatus.OPEN;
        return installment;
    }

    /**
     * Marks this installment fully paid (its full amount was received). Only an {@code OPEN} installment can be
     * paid; a paid or cancelled installment rejects the transition.
     *
     * @throws InstallmentNotPayableException if the installment is not {@code OPEN}
     */
    void markPaid() {
        if (status != InstallmentStatus.OPEN) {
            throw new InstallmentNotPayableException();
        }
        this.status = InstallmentStatus.PAID;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
