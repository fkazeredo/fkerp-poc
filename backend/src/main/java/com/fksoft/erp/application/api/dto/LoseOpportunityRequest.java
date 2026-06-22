package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request to mark an Opportunity as lost.
 *
 * @param lossReasonId the loss-reason cadastro id (required; an unknown/inactive value is rejected as 422)
 * @param note optional loss note
 */
public record LoseOpportunityRequest(
        @NotNull(message = "Motivo da perda é obrigatório") UUID lossReasonId,
        @Size(max = 2000, message = "Anotação muito longa") String note) {}
