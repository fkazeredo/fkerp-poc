package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.Receivable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational list item of a Receivable. Carries receivable data only — never Payment, Commission or Invoice
 * data. Built from the entity via {@link #from(Receivable, long, String, String)} with the resolved order
 * number, payer name and financial-responsible name.
 *
 * @param id the receivable id
 * @param commercialOrderId the source order id
 * @param orderNumber the human-friendly source order number (PC-000n)
 * @param customerName the payer name
 * @param totalAmount the total amount to receive
 * @param dueDate the due date
 * @param status the receivable status name
 * @param financialResponsibleId the financial responsible id, or {@code null}
 * @param financialResponsibleName the financial responsible name, or {@code null}
 * @param createdAt when the receivable was created
 */
public record ReceivableListItem(
        UUID id,
        UUID commercialOrderId,
        long orderNumber,
        String customerName,
        BigDecimal totalAmount,
        LocalDate dueDate,
        String status,
        UUID financialResponsibleId,
        String financialResponsibleName,
        Instant createdAt) {

    /**
     * Builds the list item from the entity and the resolved cross-aggregate names.
     *
     * @param r the receivable entity
     * @param orderNumber the resolved source order number
     * @param customerName the resolved payer name
     * @param financialResponsibleName the resolved financial-responsible name, or {@code null}
     * @return the list item
     */
    public static ReceivableListItem from(
            Receivable r, long orderNumber, String customerName, String financialResponsibleName) {
        return new ReceivableListItem(
                r.id(),
                r.commercialOrderId(),
                orderNumber,
                customerName,
                r.totalAmount(),
                r.dueDate(),
                r.status().name(),
                r.financialResponsiblePersonId(),
                financialResponsibleName,
                r.createdAt());
    }
}
