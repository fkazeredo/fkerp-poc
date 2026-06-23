package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Response after creating a Receivable: the new id and its initial status code.
 *
 * @param id the new Receivable id
 * @param status the initial status code (OPEN)
 */
public record ReceivableResponse(UUID id, String status) {}
