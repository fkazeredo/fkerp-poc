package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One installment of a Receivable schedule supplied at creation.
 *
 * @param amount the installment amount (required, non-negative)
 * @param dueDate the installment due date (required)
 * @param paymentNotes optional descriptive notes (free text — not a Payment record)
 */
public record CreateInstallmentRequest(
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotNull LocalDate dueDate,
        @Size(max = 2000) String paymentNotes) {}
