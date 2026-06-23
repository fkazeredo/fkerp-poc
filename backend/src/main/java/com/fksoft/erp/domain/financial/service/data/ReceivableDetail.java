package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.Receivable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Full detail of a Receivable, keeping the commercial origin (Order / Proposal / Opportunity / Lead /
 * Customer) traceable. Carries receivable data only — never Payment, Commission or Invoice data.
 */
public record ReceivableDetail(
        UUID id,
        UUID commercialOrderId,
        long orderNumber,
        UUID proposalId,
        UUID opportunityId,
        UUID leadId,
        UUID customerId,
        String customerName,
        UUID commercialResponsibleId,
        String commercialResponsibleName,
        UUID financialResponsibleId,
        String financialResponsibleName,
        BigDecimal totalAmount,
        LocalDate dueDate,
        String paymentNotes,
        String status,
        Instant createdAt,
        String createdByName) {

    /**
     * Builds the detail from the entity and the resolved cross-aggregate data.
     *
     * @param r the receivable entity
     * @param orderNumber the resolved source order number
     * @param customerName the resolved payer name
     * @param names a map of user id to username (for the responsibles and the creator)
     * @return the detail read model
     */
    public static ReceivableDetail from(Receivable r, long orderNumber, String customerName, Map<UUID, String> names) {
        return new ReceivableDetail(
                r.id(),
                r.commercialOrderId(),
                orderNumber,
                r.proposalId(),
                r.opportunityId(),
                r.leadId(),
                r.customerId(),
                customerName,
                r.commercialResponsiblePersonId(),
                nameOf(names, r.commercialResponsiblePersonId()),
                r.financialResponsiblePersonId(),
                nameOf(names, r.financialResponsiblePersonId()),
                r.totalAmount(),
                r.dueDate(),
                r.paymentNotes(),
                r.status().name(),
                r.createdAt(),
                nameOf(names, r.createdBy()));
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
