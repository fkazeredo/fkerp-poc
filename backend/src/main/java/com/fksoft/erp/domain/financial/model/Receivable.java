package com.fksoft.erp.domain.financial.model;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.financial.exception.InstallmentNotPayableException;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
import com.fksoft.erp.domain.financial.exception.PaymentExceedsOutstandingException;
import com.fksoft.erp.domain.financial.exception.PaymentInstallmentNotFoundException;
import com.fksoft.erp.domain.financial.exception.PaymentNotFoundException;
import com.fksoft.erp.domain.financial.service.data.InstallmentInput;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A Receivable (Financial Operations): the amount the company has to receive from a client for a closed,
 * confirmed deal. It is created from a {@link CommercialOrder} whose booking is CONFIRMED, snapshotting the
 * commercial total and preserving the full commercial origin (Order / Proposal / Opportunity / Lead /
 * Customer / commercial responsible). It is NOT a Payment, Commission, Invoice or accounting record; creating
 * it registers no Payment and creates no Commission, Invoice, Booking or Customer Care data. It starts
 * {@code OPEN}. Payments, installments and the status lifecycle beyond {@code OPEN} are later slices.
 */
@Entity
@Table(name = "receivables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receivable {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The source Commercial Order this Receivable bills (preserved, immutable).
    @NotNull
    @Column(name = "commercial_order_id", nullable = false, updatable = false)
    private UUID commercialOrderId;

    // Commercial origin, kept traceable (denormalized from the Order).
    @NotNull
    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @NotNull
    @Column(name = "opportunity_id", nullable = false, updatable = false)
    private UUID opportunityId;

    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    // The payer — the Customer (commercial graduation of the Lead).
    @NotNull
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    // The commercial responsible preserved from the Order (or null when unassigned).
    @Column(name = "commercial_responsible_person_id")
    private UUID commercialResponsiblePersonId;

    // The financial responsible (optional; who in Finance owns this Receivable).
    @Column(name = "financial_responsible_person_id")
    private UUID financialResponsiblePersonId;

    @NotNull
    @PositiveOrZero
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Size(max = 2000)
    @Column(name = "payment_notes")
    private String paymentNotes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceivableStatus status;

    // The installment schedule (part of the aggregate): the slices of the amount due. Always sums to the total
    // (one full-amount installment when the Receivable is not split). Defined at creation.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "receivable_id", nullable = false)
    private List<ReceivableInstallment> installments = new ArrayList<>();

    // The payment history (part of the aggregate): the payments received against the installments. Append-only.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "receivable_id", nullable = false)
    private List<ReceivablePayment> payments = new ArrayList<>();

    // Denormalized payment standing, maintained on every payment: the total received and the latest payment date.
    @NotNull
    @PositiveOrZero
    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

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
     * Creates a Receivable from a Commercial Order whose booking is CONFIRMED, snapshotting the commercial
     * total and preserving the commercial origin + the Customer (payer). Starts {@code OPEN}. Registers no
     * Payment and creates no Commission, Invoice, Booking or Customer Care data.
     *
     * @param order the source Commercial Order; its {@code bookingStatus} must be {@code CONFIRMED}
     * @param customer the payer (the Customer materialized from the Order's Lead)
     * @param dueDate the receivable's reference due date (required; used for the single installment when the
     *     schedule is not split)
     * @param paymentNotes optional descriptive payment notes (free text — not a Payment record)
     * @param financialResponsibleId the financial responsible, or {@code null}
     * @param installmentInputs the installment schedule; empty/{@code null} ⇒ one full-amount installment
     * @param createdBy id of the user creating the Receivable
     * @return a new, unsaved Receivable
     * @throws OrderBookingNotConfirmedException if the Order's booking is not CONFIRMED
     * @throws InstallmentScheduleInvalidException if the installments do not sum to the total or are invalid
     */
    public static Receivable createFromOrder(
            CommercialOrder order,
            Customer customer,
            LocalDate dueDate,
            String paymentNotes,
            UUID financialResponsibleId,
            List<InstallmentInput> installmentInputs,
            UUID createdBy) {
        if (!"CONFIRMED".equals(order.bookingStatus())) {
            throw new OrderBookingNotConfirmedException();
        }
        Receivable receivable = new Receivable();
        receivable.id = UUID.randomUUID();
        receivable.commercialOrderId = order.id();
        receivable.proposalId = order.proposalId();
        receivable.opportunityId = order.opportunityId();
        receivable.leadId = order.leadId();
        receivable.customerId = customer.id();
        receivable.commercialResponsiblePersonId = order.responsiblePersonId();
        receivable.financialResponsiblePersonId = financialResponsibleId;
        receivable.totalAmount = order.total();
        receivable.dueDate = dueDate;
        receivable.paymentNotes = emptyToNull(paymentNotes);
        receivable.status = ReceivableStatus.OPEN;
        receivable.createdBy = createdBy;
        receivable.updatedBy = createdBy;
        receivable.scheduleInstallments(installmentInputs);
        return receivable;
    }

    /**
     * Builds the installment schedule. An empty/{@code null} schedule yields a single installment for the full
     * total (every Receivable always has at least one installment). Otherwise the installments must sum to the
     * total (each non-negative, with a due date), and are numbered 1..n in the given order. Creates no Payment,
     * Commission or Invoice data.
     *
     * @param inputs the installments, or empty/{@code null} for a single full-amount installment
     * @throws InstallmentScheduleInvalidException if the installments do not sum to the total or are invalid
     */
    private void scheduleInstallments(List<InstallmentInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            installments.add(ReceivableInstallment.of(1, totalAmount, dueDate, null));
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (InstallmentInput input : inputs) {
            if (input.amount() == null || input.amount().signum() < 0 || input.dueDate() == null) {
                throw new InstallmentScheduleInvalidException();
            }
            sum = sum.add(input.amount());
        }
        if (sum.setScale(2, RoundingMode.HALF_UP).compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new InstallmentScheduleInvalidException();
        }
        int number = 1;
        for (InstallmentInput input : inputs) {
            installments.add(ReceivableInstallment.of(number++, input.amount(), input.dueDate(), input.paymentNotes()));
        }
    }

    /**
     * Registers a payment for one of this Receivable's installments. The payment may settle the installment
     * fully (amount == the installment's outstanding) or partially (a smaller amount); it may NOT exceed the
     * installment's outstanding amount (overpayment is out of scope). The installment becomes {@code PAID} when
     * fully covered, otherwise {@code PARTIALLY_PAID}; the payment is recorded in the aggregate's history, the
     * paid total and the latest payment date are updated, and the Receivable status is consolidated ({@code PAID}
     * when nothing is outstanding, {@code PARTIALLY_PAID} when something has been received but a balance remains).
     * Creates no Commission, Invoice or bank-reconciliation data and never touches the source Order, Lead or
     * Customer.
     *
     * @param installmentId the target installment (must belong to this Receivable and not be resolved)
     * @param amount the payment amount (positive, not exceeding the installment's outstanding)
     * @param paymentDate the date the payment was received
     * @param method the payment method (an active cadastro value)
     * @param note optional free-text reference/note
     * @param registeredBy id of the user registering the payment
     * @return the registered payment
     * @throws PaymentInstallmentNotFoundException if the installment is not part of this Receivable
     * @throws InstallmentNotPayableException if the installment is already paid or cancelled
     * @throws PaymentExceedsOutstandingException if the amount exceeds the installment's outstanding amount
     */
    public ReceivablePayment registerPayment(
            UUID installmentId,
            BigDecimal amount,
            LocalDate paymentDate,
            PaymentMethod method,
            String note,
            UUID registeredBy) {
        ReceivableInstallment installment = installments.stream()
                .filter(i -> i.id().equals(installmentId))
                .findFirst()
                .orElseThrow(PaymentInstallmentNotFoundException::new);
        installment.applyPayment(amount);
        ReceivablePayment payment =
                ReceivablePayment.of(installmentId, amount, paymentDate, method, note, registeredBy);
        payments.add(payment);
        amountPaid = amountPaid.add(payment.amount());
        if (lastPaymentDate == null || paymentDate.isAfter(lastPaymentDate)) {
            lastPaymentDate = paymentDate;
        }
        consolidateStatus();
        updatedBy = registeredBy;
        return payment;
    }

    /**
     * Reverses a registered payment of this Receivable (a payment-entry correction). The payment is marked
     * reversed (kept in history, never deleted), the settled installment's paid amount and status are re-derived,
     * the Receivable's paid total and latest payment date are recomputed from the remaining (non-reversed)
     * payments, and the Receivable status is consolidated — a previously {@code PAID} receivable returns to
     * {@code PARTIALLY_PAID} or {@code OPEN} per the remaining paid amount. Creates no refund, Commission or
     * Customer Care data and never touches the source Order, Lead or Customer.
     *
     * @param paymentId the payment to reverse (must belong to this Receivable and not already be reversed)
     * @param reason the reason for the reversal (required at the request boundary)
     * @param reversedBy id of the user reversing the payment
     * @param reversedAt when the reversal happens
     * @return the reversed payment
     * @throws PaymentNotFoundException if the payment is not part of this Receivable
     * @throws PaymentAlreadyReversedException if the payment has already been reversed
     */
    public ReceivablePayment reversePayment(UUID paymentId, String reason, UUID reversedBy, Instant reversedAt) {
        ReceivablePayment payment = payments.stream()
                .filter(p -> p.id().equals(paymentId))
                .findFirst()
                .orElseThrow(PaymentNotFoundException::new);
        payment.reverse(reason, reversedBy, reversedAt);
        ReceivableInstallment installment = installments.stream()
                .filter(i -> i.id().equals(payment.installmentId()))
                .findFirst()
                .orElseThrow(PaymentNotFoundException::new);
        installment.reverseAmount(payment.amount());
        amountPaid = amountPaid.subtract(payment.amount());
        lastPaymentDate = payments.stream()
                .filter(p -> !p.reversed())
                .map(ReceivablePayment::paymentDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
        consolidateStatus();
        updatedBy = reversedBy;
        return payment;
    }

    /**
     * Consolidates the Receivable status from the paid total after a payment: {@code PAID} when nothing is
     * outstanding, {@code PARTIALLY_PAID} when something has been received but a balance remains, otherwise
     * {@code OPEN} (no payment yet). A still-outstanding {@code OVERDUE} receivable stays {@code OVERDUE} (a
     * payment never "un-overdues" a past-due receivable; the OPEN/PARTIALLY_PAID → OVERDUE transition is owned by
     * {@link #markOverdueIfPastDue}). Never overrides an explicit {@code CANCELLED}.
     */
    private void consolidateStatus() {
        if (status == ReceivableStatus.CANCELLED) {
            return;
        }
        if (totalAmount.subtract(amountPaid).signum() <= 0) {
            status = ReceivableStatus.PAID;
        } else if (status == ReceivableStatus.OVERDUE) {
            // Still outstanding and already past due — a partial payment keeps it OVERDUE.
            return;
        } else if (amountPaid.signum() > 0) {
            status = ReceivableStatus.PARTIALLY_PAID;
        } else {
            status = ReceivableStatus.OPEN;
        }
    }

    /**
     * Transitions an operational ({@code OPEN}/{@code PARTIALLY_PAID}) Receivable to {@code OVERDUE} when it has a
     * balance remaining and at least one of its installments is past due as of {@code today}. Idempotent — a
     * {@code PAID}/{@code CANCELLED}/already-{@code OVERDUE} receivable is left untouched. Owned by the daily
     * overdue check (a Receivable is never "un-overdued"; a payment that settles it moves it to {@code PAID}).
     *
     * @param today the reference (current) date
     * @return {@code true} if the status transitioned to {@code OVERDUE}
     */
    public boolean markOverdueIfPastDue(LocalDate today) {
        if (status != ReceivableStatus.OPEN && status != ReceivableStatus.PARTIALLY_PAID) {
            return false;
        }
        if (totalAmount.subtract(amountPaid).signum() <= 0) {
            return false;
        }
        if (installments.stream().noneMatch(i -> i.isPastDue(today))) {
            return false;
        }
        status = ReceivableStatus.OVERDUE;
        return true;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
