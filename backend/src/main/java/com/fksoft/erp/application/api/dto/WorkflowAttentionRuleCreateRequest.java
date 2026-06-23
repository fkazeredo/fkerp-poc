package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request to create a custom attention rule on a workflow.
 *
 * @param conditionKey the catalog condition key (must be valid for the workflow)
 * @param thresholdDays the optional staleness window in days (or {@code null})
 * @param stateValue the optional state/status value (or {@code null})
 * @param code the stable reason code (uppercase letters, digits and underscore)
 * @param label the display label
 * @param sortOrder the evaluation/display order
 */
public record WorkflowAttentionRuleCreateRequest(
        @NotBlank(message = "Condição é obrigatória") String conditionKey,
        @PositiveOrZero Integer thresholdDays,
        String stateValue,
        @NotBlank(message = "Código é obrigatório")
                @Pattern(regexp = "[A-Z0-9_]+", message = "Código deve usar MAIÚSCULAS, dígitos ou _")
                String code,
        @NotBlank(message = "Descrição é obrigatória") String label,
        @PositiveOrZero int sortOrder) {}
