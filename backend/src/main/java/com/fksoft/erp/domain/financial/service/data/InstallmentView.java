package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.ReceivableInstallment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of a single Receivable installment, including its payment progress. Carries installment data only —
 * never Commission or Invoice data.
 *
 * @param id the installment id (the target for registering a payment)
 * @param number the 1-based position in the schedule
 * @param amount the installment amount
 * @param amountPaid the amount already received against this installment
 * @param outstanding the amount still due ({@code amount − amountPaid})
 * @param dueDate the installment due date
 * @param status the installment status name
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
        String paymentNotes) {

    /**
     * Builds the view from the installment entity.
     *
     * @param installment the installment
     * @return the read view
     */
    public static InstallmentView from(ReceivableInstallment installment) {
        return new InstallmentView(
                installment.id(),
                installment.number(),
                installment.amount(),
                installment.amountPaid(),
                installment.outstanding(),
                installment.dueDate(),
                installment.status().name(),
                installment.paymentNotes());
    }
}
