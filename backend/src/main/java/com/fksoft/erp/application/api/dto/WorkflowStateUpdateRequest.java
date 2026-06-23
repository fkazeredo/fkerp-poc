package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request to update a workflow state's editable attributes (the {@code code} and category stay as seeded).
 *
 * @param label the display label
 * @param sortOrder the display order
 * @param active the active flag
 */
public record WorkflowStateUpdateRequest(
        @NotBlank(message = "Descrição é obrigatória") String label, @PositiveOrZero int sortOrder, boolean active) {}
