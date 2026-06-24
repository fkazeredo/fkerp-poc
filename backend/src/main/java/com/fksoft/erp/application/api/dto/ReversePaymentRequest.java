package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to reverse a registered payment of a Receivable (a payment-entry correction).
 *
 * @param reason the reason for the reversal — required, free text
 */
public record ReversePaymentRequest(@NotBlank @Size(max = 2000) String reason) {}
