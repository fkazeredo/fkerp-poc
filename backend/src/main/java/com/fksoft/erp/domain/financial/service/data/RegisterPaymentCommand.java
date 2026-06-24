package com.fksoft.erp.domain.financial.service.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to register a full payment for a Receivable installment.
 *
 * @param paymentMethodId the payment method (an active cadastro value)
 * @param amount the amount received (must equal the installment amount — full payment only)
 * @param paymentDate the date the payment was received
 * @param note optional free-text reference/note
 */
public record RegisterPaymentCommand(UUID paymentMethodId, BigDecimal amount, LocalDate paymentDate, String note) {}
