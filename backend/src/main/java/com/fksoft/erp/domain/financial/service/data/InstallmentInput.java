package com.fksoft.erp.domain.financial.service.data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single installment of a Receivable schedule supplied at creation.
 *
 * @param amount the installment amount (required, non-negative)
 * @param dueDate the installment due date (required)
 * @param paymentNotes optional descriptive notes (free text — not a Payment record)
 */
public record InstallmentInput(BigDecimal amount, LocalDate dueDate, String paymentNotes) {}
