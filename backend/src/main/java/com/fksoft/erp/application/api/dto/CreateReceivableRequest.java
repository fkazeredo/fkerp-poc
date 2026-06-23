package com.fksoft.erp.application.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request to create a Receivable from a Commercial Order with a CONFIRMED booking.
 *
 * @param commercialOrderId the source Commercial Order id (required; its booking must be CONFIRMED)
 * @param dueDate the reference due date (required; used for the single installment when not split)
 * @param paymentNotes optional descriptive payment notes (free text — not a Payment record)
 * @param financialResponsiblePersonId the financial responsible, or {@code null}
 * @param installments the optional installment schedule; absent/empty ⇒ one full-amount installment. When
 *     present, the installments must sum to the order total.
 */
public record CreateReceivableRequest(
        @NotNull UUID commercialOrderId,
        @NotNull LocalDate dueDate,
        @Size(max = 2000) String paymentNotes,
        UUID financialResponsiblePersonId,
        @Valid List<CreateInstallmentRequest> installments) {}
