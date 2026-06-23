package com.fksoft.erp.domain.financial.model;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.financial.exception.InstallmentScheduleInvalidException;
import com.fksoft.erp.domain.financial.exception.OrderBookingNotConfirmedException;
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

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
