package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Request to mark a booking item as failed.
 *
 * @param failureReasonId the failure-reason cadastro id (required; an unknown/inactive value is rejected as 422)
 * @param failureNote an optional note
 * @param failedAt when the failure was determined (required; not in the future)
 */
public record FailBookingItemRequest(
        @NotNull(message = "Motivo da falha é obrigatório") UUID failureReasonId,
        @Size(max = 2000, message = "Texto muito longo") String failureNote,
        @NotNull(message = "Data da falha é obrigatória")
                @PastOrPresent(message = "A data da falha não pode ser no futuro")
                Instant failedAt) {}
