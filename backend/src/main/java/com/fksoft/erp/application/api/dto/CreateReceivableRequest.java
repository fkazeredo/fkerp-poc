package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create a Receivable from a Commercial Order with a CONFIRMED booking.
 *
 * @param commercialOrderId the source Commercial Order id (required; its booking must be CONFIRMED)
 * @param dueDate the single due date (required)
 * @param paymentNotes optional descriptive payment notes (free text — not a Payment record)
 * @param financialResponsiblePersonId the financial responsible, or {@code null}
 */
public record CreateReceivableRequest(
        @NotNull UUID commercialOrderId,
        @NotNull LocalDate dueDate,
        @Size(max = 2000) String paymentNotes,
        UUID financialResponsiblePersonId) {}
