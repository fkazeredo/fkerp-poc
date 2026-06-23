package com.fksoft.erp.domain.financial.service.data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A Commercial Order eligible to originate a Receivable (booking CONFIRMED and without an active Receivable),
 * for the create form's selector. Carries commercial-order data only.
 *
 * @param orderId the order id
 * @param number the human-friendly order number (rendered as PC-000n)
 * @param customerName the payer name (the Customer materialized from the source Lead)
 * @param total the commercial total to be received
 */
public record EligibleOrder(UUID orderId, long number, String customerName, BigDecimal total) {}
