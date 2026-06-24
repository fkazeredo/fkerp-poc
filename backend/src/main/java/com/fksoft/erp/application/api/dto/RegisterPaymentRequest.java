package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to register a full payment for a Receivable installment.
 *
 * @param paymentMethodId the payment method id (an active cadastro value) — required
 * @param amount the amount received — required, positive (must equal the installment amount, validated server-side)
 * @param paymentDate the date the payment was received — required, not in the future
 * @param note optional free-text reference/note
 */
public record RegisterPaymentRequest(
        @NotNull UUID paymentMethodId,
        @NotNull @Positive BigDecimal amount,
        @NotNull @PastOrPresent LocalDate paymentDate,
        @Size(max = 2000) String note) {}
