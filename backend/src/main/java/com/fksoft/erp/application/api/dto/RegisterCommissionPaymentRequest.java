package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body to register a manual commission payment (Approved → Paid). The amount must equal the approved
 * commission amount (full payment only); the payment date cannot be in the future; the method is a payment-method
 * cadastro id; the note is optional.
 *
 * @param paymentMethodId the payment-method cadastro id (required)
 * @param amount the paid amount (required, must equal the commission amount)
 * @param paymentDate the payment date (required, not in the future)
 * @param note an optional free-text reference/note
 */
public record RegisterCommissionPaymentRequest(
        @NotNull UUID paymentMethodId,
        @NotNull @Positive BigDecimal amount,
        @NotNull @PastOrPresent LocalDate paymentDate,
        @Size(max = 2000) String note) {}
