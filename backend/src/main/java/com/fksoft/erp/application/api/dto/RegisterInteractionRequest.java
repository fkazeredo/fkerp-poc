package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Request to register a Lead interaction.
 *
 * @param typeId the (active) interaction type id (required)
 * @param resultId the (active) interaction result id (required)
 * @param description the interaction description (required)
 * @param occurredAt when the interaction happened (required, not in the future)
 * @param nextContactAt optional scheduled next contact
 */
public record RegisterInteractionRequest(
        @NotNull(message = "Tipo de interação é obrigatório") UUID typeId,
        @NotNull(message = "Resultado da interação é obrigatório") UUID resultId,
        @NotBlank(message = "Descrição é obrigatória") @Size(max = 4000, message = "Descrição muito longa")
                String description,
        @NotNull(message = "Data da interação é obrigatória")
                @PastOrPresent(message = "A data da interação não pode ser no futuro")
                Instant occurredAt,
        Instant nextContactAt) {}
