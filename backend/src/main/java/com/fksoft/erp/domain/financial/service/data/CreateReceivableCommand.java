package com.fksoft.erp.domain.financial.service.data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to create a Receivable from a confirmed Commercial Order.
 *
 * @param commercialOrderId the source Commercial Order (must have a CONFIRMED booking)
 * @param dueDate the due date (required)
 * @param paymentNotes optional descriptive payment notes (free text — not a Payment record)
 * @param financialResponsiblePersonId the financial responsible, or {@code null}
 */
public record CreateReceivableCommand(
        UUID commercialOrderId, LocalDate dueDate, String paymentNotes, UUID financialResponsiblePersonId) {}
