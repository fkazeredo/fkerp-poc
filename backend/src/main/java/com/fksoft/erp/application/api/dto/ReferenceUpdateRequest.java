package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to update a reference-data value (code is immutable).
 *
 * @param label display label
 * @param sortOrder sort order
 * @param active active flag (false soft-deletes)
 */
public record ReferenceUpdateRequest(
        @NotBlank(message = "Descrição é obrigatória") String label, int sortOrder, boolean active) {}
