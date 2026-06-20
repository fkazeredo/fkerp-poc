package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Trivial create response for a new Commercial Order. The full record (status, items, totals, source
 * references) is read from {@code GET /api/orders/{id}}.
 *
 * @param id the new order id
 */
public record OrderResponse(UUID id) {}
