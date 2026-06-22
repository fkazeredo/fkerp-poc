package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to register a commercial activity on an Opportunity.
 *
 * @param typeId the activity-type cadastro id (required; an unknown/inactive value is rejected as 422)
 * @param resultId the activity-result cadastro id (required; an unknown/inactive value is rejected as 422)
 * @param description what happened (required)
 * @param occurredAt when the activity happened (required; not in the future)
 * @param nextActionDate optional planned next action date
 */
public record RegisterOpportunityActivityRequest(
        @NotNull(message = "Tipo da atividade é obrigatório") UUID typeId,
        @NotNull(message = "Resultado da atividade é obrigatório") UUID resultId,
        @NotBlank(message = "Descrição é obrigatória") @Size(max = 4000, message = "Descrição muito longa")
                String description,
        @NotNull(message = "Data da atividade é obrigatória")
                @PastOrPresent(message = "A data da atividade não pode ser no futuro")
                Instant occurredAt,
        LocalDate nextActionDate) {}
