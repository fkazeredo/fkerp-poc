package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.ReceivableInstallment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of a single Receivable installment, including its payment progress and whether it is overdue.
 * Carries installment data only — never Commission or Invoice data.
 *
 * @param id the installment id (the target for registering a payment)
 * @param number the 1-based position in the schedule
 * @param amount the installment amount
 * @param amountPaid the amount already received against this installment
 * @param outstanding the amount still due ({@code amount − amountPaid})
 * @param dueDate the installment due date
 * @param status the installment status name
 * @param overdue whether this installment is past due with a balance ({@code OPEN}/{@code PARTIALLY_PAID} and past
 *     its due date — paid and cancelled installments are never overdue)
 * @param paymentNotes optional descriptive notes
 */
public record InstallmentView(
        UUID id,
        int number,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal outstanding,
        LocalDate dueDate,
        String status,
        boolean overdue,
        String paymentNotes) {

    /**
     * Builds the view from the installment entity, computing the overdue flag as of {@code today}.
     *
     * @param installment the installment
     * @param today the current date, for the per-installment overdue computation
     * @return the read view
     */
    public static InstallmentView from(ReceivableInstallment installment, LocalDate today) {
        return new InstallmentView(
                installment.id(),
                installment.number(),
                installment.amount(),
                installment.amountPaid(),
                installment.outstanding(),
                installment.dueDate(),
                installment.status().name(),
                installment.isPastDue(today),
                installment.paymentNotes());
    }
}
