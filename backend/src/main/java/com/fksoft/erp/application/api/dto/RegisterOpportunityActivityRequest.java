package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Request to register a commercial activity on an Opportunity.
 *
 * @param type the activity type (required; an unknown value is rejected as a 400)
 * @param result the activity outcome (required)
 * @param description what happened (required)
 * @param occurredAt when the activity happened (required; not in the future)
 * @param nextActionDate optional planned next action date
 */
public record RegisterOpportunityActivityRequest(
        @NotNull(message = "Tipo da atividade é obrigatório") OpportunityActivityType type,
        @NotNull(message = "Resultado da atividade é obrigatório") OpportunityActivityResult result,
        @NotBlank(message = "Descrição é obrigatória") @Size(max = 4000, message = "Descrição muito longa")
                String description,
        @NotNull(message = "Data da atividade é obrigatória")
                @PastOrPresent(message = "A data da atividade não pode ser no futuro")
                Instant occurredAt,
        LocalDate nextActionDate) {}
