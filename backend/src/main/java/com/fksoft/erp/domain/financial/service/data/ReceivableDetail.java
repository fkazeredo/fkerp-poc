package com.fksoft.erp.domain.financial.service.data;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableInstallment;
import com.fksoft.erp.domain.financial.model.ReceivablePayment;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full detail of a Receivable, for consultation: its summary, the traceable commercial origin (Order / Proposal
 * / Opportunity / Lead / Customer), the installment schedule, the payment history and the payment standing
 * (paid / outstanding / overdue). Carries receivable data only — never Commission, bank-reconciliation or
 * tax-invoice data. {@code amountPaid} is the sum of the registered payments and {@code outstandingAmount} is the
 * total minus the amount paid.
 */
public record ReceivableDetail(
        UUID id,
        UUID commercialOrderId,
        long orderNumber,
        UUID proposalId,
        String proposalReference,
        UUID opportunityId,
        String opportunityReference,
        UUID leadId,
        UUID customerId,
        String customerName,
        UUID commercialResponsibleId,
        String commercialResponsibleName,
        UUID financialResponsibleId,
        String financialResponsibleName,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal outstandingAmount,
        LocalDate dueDate,
        boolean overdue,
        String paymentNotes,
        String status,
        List<InstallmentView> installments,
        List<PaymentView> payments,
        Instant createdAt,
        String createdByName) {

    /**
     * Builds the detail from the entity and the resolved cross-aggregate data. {@code amountPaid} is the sum of
     * the registered payments; {@code overdue} is the stored OVERDUE status (set by the daily overdue check), and
     * each installment carries its own read-time {@code overdue} flag.
     *
     * @param r the receivable entity
     * @param orderNumber the resolved source order number
     * @param customerName the resolved payer name
     * @param proposalReference the resolved source Proposal title (commercial reference), or {@code null}
     * @param opportunityReference the resolved source Opportunity name (commercial reference), or {@code null}
     * @param names a map of user id to username (for the responsibles, the creator and the payment registrants)
     * @param today the current date, for the overdue computation
     * @return the detail read model
     */
    public static ReceivableDetail from(
            Receivable r,
            long orderNumber,
            String customerName,
            String proposalReference,
            String opportunityReference,
            Map<UUID, String> names,
            LocalDate today) {
        BigDecimal amountPaid = r.amountPaid();
        Map<UUID, Integer> installmentNumbers = r.installments().stream()
                .collect(Collectors.toMap(ReceivableInstallment::id, ReceivableInstallment::number));
        return new ReceivableDetail(
                r.id(),
                r.commercialOrderId(),
                orderNumber,
                r.proposalId(),
                proposalReference,
                r.opportunityId(),
                opportunityReference,
                r.leadId(),
                r.customerId(),
                customerName,
                r.commercialResponsiblePersonId(),
                nameOf(names, r.commercialResponsiblePersonId()),
                r.financialResponsiblePersonId(),
                nameOf(names, r.financialResponsiblePersonId()),
                r.totalAmount(),
                amountPaid,
                r.totalAmount().subtract(amountPaid),
                r.dueDate(),
                isOverdue(r),
                r.paymentNotes(),
                r.status().name(),
                r.installments().stream()
                        .sorted(Comparator.comparingInt(ReceivableInstallment::number))
                        .map(i -> InstallmentView.from(i, today))
                        .toList(),
                r.payments().stream()
                        .sorted(Comparator.comparing(ReceivablePayment::paymentDate)
                                .thenComparing(
                                        ReceivablePayment::registeredAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(p -> PaymentView.from(
                                p,
                                installmentNumbers.getOrDefault(p.installmentId(), 0),
                                nameOf(names, p.registeredBy()),
                                nameOf(names, p.reversedBy())))
                        .toList(),
                r.createdAt(),
                nameOf(names, r.createdBy()));
    }

    // Overdue is the stored OVERDUE status — the single source of truth, set by the daily overdue check
    // (past due with a balance, per-installment-precise). The per-installment overdue lives on InstallmentView.
    private static boolean isOverdue(Receivable r) {
        return r.status() == ReceivableStatus.OVERDUE;
    }

    private static String nameOf(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }
}
