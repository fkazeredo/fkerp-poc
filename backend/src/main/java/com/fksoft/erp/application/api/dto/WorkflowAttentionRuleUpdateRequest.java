package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request to update an attention rule (the {@code code} and {@code conditionKey} stay immutable).
 *
 * @param label the display label
 * @param thresholdDays the optional staleness window in days (or {@code null})
 * @param sortOrder the evaluation/display order
 * @param active the active flag (false disables the rule)
 */
public record WorkflowAttentionRuleUpdateRequest(
        @NotBlank(message = "Descrição é obrigatória") String label,
        @PositiveOrZero Integer thresholdDays,
        @PositiveOrZero int sortOrder,
        boolean active) {}
