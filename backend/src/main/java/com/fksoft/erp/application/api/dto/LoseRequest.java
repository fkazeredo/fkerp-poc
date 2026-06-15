package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request to mark a Lead as lost.
 *
 * @param lossReasonId the (active) loss reason id (required)
 * @param note optional loss note
 */
public record LoseRequest(
        @NotNull(message = "Motivo da perda é obrigatório") UUID lossReasonId,
        @Size(max = 2000, message = "Anotação muito longa") String note) {}
