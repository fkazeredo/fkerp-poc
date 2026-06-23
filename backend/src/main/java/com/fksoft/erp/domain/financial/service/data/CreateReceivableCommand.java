package com.fksoft.erp.domain.financial.service.data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Input to create a Receivable from a confirmed Commercial Order.
 *
 * @param commercialOrderId the source Commercial Order (must have a CONFIRMED booking)
 * @param dueDate the receivable's reference due date (required; used for the single installment when not split)
 * @param paymentNotes optional descriptive payment notes (free text — not a Payment record)
 * @param financialResponsiblePersonId the financial responsible, or {@code null}
 * @param installments the installment schedule; empty/{@code null} ⇒ one full-amount installment
 */
public record CreateReceivableCommand(
        UUID commercialOrderId,
        LocalDate dueDate,
        String paymentNotes,
        UUID financialResponsiblePersonId,
        List<InstallmentInput> installments) {}
