package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational list item of a Receivable — the information a financial user needs to prioritize collection.
 * Carries receivable + commercial-origin data only — never Commission, Invoice or bank-reconciliation data.
 * Built from the entity via {@link #from} with the resolved order number, payer name and responsible names. The
 * {@code amountPaid} / {@code outstandingAmount} / {@code lastPaymentDate} fields reflect the registered payments
 * (denormalized on the Receivable).
 *
 * @param id the receivable id
 * @param commercialOrderId the source order id
 * @param orderNumber the human-friendly source order number (PC-000n)
 * @param customerName the payer (billing party) name
 * @param totalAmount the total amount to receive
 * @param amountPaid the amount already received
 * @param outstandingAmount the amount still to receive ({@code totalAmount − amountPaid})
 * @param status the receivable status name
 * @param dueDate the next due date
 * @param overdue whether the receivable is past due and still requires follow-up
 * @param commercialResponsibleId the commercial responsible id, or {@code null}
 * @param commercialResponsibleName the commercial responsible name, or {@code null}
 * @param financialResponsibleId the financial responsible id, or {@code null}
 * @param financialResponsibleName the financial responsible name, or {@code null}
 * @param createdAt when the receivable was created
 * @param lastPaymentDate the date of the latest registered payment, or {@code null} when none yet
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
        LocalDate lastPaymentDate) {

    /**
     * Builds the list item from the entity and the resolved cross-aggregate names. {@code amountPaid} and
     * {@code lastPaymentDate} read the denormalized payment standing; {@code overdue} is the stored OVERDUE status
     * (set by the daily overdue check).
     *
     * @param r the receivable entity
     * @param orderNumber the resolved source order number
     * @param customerName the resolved payer name
     * @param commercialResponsibleName the resolved commercial-responsible name, or {@code null}
     * @param financialResponsibleName the resolved financial-responsible name, or {@code null}
     * @return the list item
     */
    public static ReceivableListItem from(
            Receivable r,
            long orderNumber,
            String customerName,
            String commercialResponsibleName,
            String financialResponsibleName) {
        BigDecimal amountPaid = r.amountPaid();
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
                r.status() == ReceivableStatus.OVERDUE,
                r.commercialResponsiblePersonId(),
                commercialResponsibleName,
                r.financialResponsiblePersonId(),
                financialResponsibleName,
                r.createdAt(),
                r.lastPaymentDate());
    }
}
