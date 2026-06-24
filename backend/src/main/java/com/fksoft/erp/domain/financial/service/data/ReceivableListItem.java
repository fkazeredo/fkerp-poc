package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational list item of a Receivable — the information a financial user needs to prioritize collection.
 * Carries receivable + commercial-origin data only — never Payment, Commission, Invoice or bank-reconciliation
 * data. Built from the entity via {@link #from} with the resolved order number, payer name and responsible
 * names. The {@code amountPaid} / {@code outstandingAmount} / {@code lastPaymentDate} fields reflect the current
 * (no-payment) state — zero / full total / none — and become real with the payment slice.
 *
 * @param id the receivable id
 * @param commercialOrderId the source order id
 * @param orderNumber the human-friendly source order number (PC-000n)
 * @param customerName the payer (billing party) name
 * @param totalAmount the total amount to receive
 * @param amountPaid the amount already received (zero until the payment slice)
 * @param outstandingAmount the amount still to receive ({@code totalAmount − amountPaid})
 * @param status the receivable status name
 * @param dueDate the next due date
 * @param overdue whether the receivable is past due and still requires follow-up
 * @param commercialResponsibleId the commercial responsible id, or {@code null}
 * @param commercialResponsibleName the commercial responsible name, or {@code null}
 * @param financialResponsibleId the financial responsible id, or {@code null}
 * @param financialResponsibleName the financial responsible name, or {@code null}
 * @param createdAt when the receivable was created
 * @param lastPaymentDate when the last payment was registered (none until the payment slice)
 */
public record ReceivableListItem(
        UUID id,
        UUID commercialOrderId,
        long orderNumber,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal outstandingAmount,
        String status,
        LocalDate dueDate,
        boolean overdue,
        UUID commercialResponsibleId,
        String commercialResponsibleName,
        UUID financialResponsibleId,
        String financialResponsibleName,
        Instant createdAt,
        Instant lastPaymentDate) {

    /**
     * Builds the list item from the entity and the resolved cross-aggregate names. {@code amountPaid} is zero and
     * {@code lastPaymentDate} is {@code null} until the payment slice; {@code overdue} is computed from the due
     * date and the status (settled receivables are never overdue).
     *
     * @param r the receivable entity
     * @param orderNumber the resolved source order number
     * @param customerName the resolved payer name
     * @param commercialResponsibleName the resolved commercial-responsible name, or {@code null}
     * @param financialResponsibleName the resolved financial-responsible name, or {@code null}
     * @param today the current date, for the overdue computation
     * @return the list item
     */
    public static ReceivableListItem from(
            Receivable r,
            long orderNumber,
            String customerName,
            String commercialResponsibleName,
            String financialResponsibleName,
            LocalDate today) {
        BigDecimal amountPaid = BigDecimal.ZERO; // no payments yet (payment slice)
        return new ReceivableListItem(
                r.id(),
                r.commercialOrderId(),
                orderNumber,
                customerName,
                r.totalAmount(),
                amountPaid,
                r.totalAmount().subtract(amountPaid),
                r.status().name(),
                r.dueDate(),
                isOverdue(r, today),
                r.commercialResponsiblePersonId(),
                commercialResponsibleName,
                r.financialResponsiblePersonId(),
                financialResponsibleName,
                r.createdAt(),
                null); // last payment date — none until the payment slice
    }

    // Overdue = past the (next) due date and still requiring follow-up (not PAID, not CANCELLED).
    private static boolean isOverdue(Receivable r, LocalDate today) {
        return r.dueDate() != null
                && r.dueDate().isBefore(today)
                && r.status() != ReceivableStatus.PAID
                && r.status() != ReceivableStatus.CANCELLED;
    }
}
