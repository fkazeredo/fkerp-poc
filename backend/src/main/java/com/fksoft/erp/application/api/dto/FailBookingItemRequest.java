package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.booking.model.BookingFailureReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request to mark a booking item as failed.
 *
 * @param failureReason the failure reason (required; an unknown value is rejected as a 400)
 * @param failureNote an optional note
 * @param failedAt when the failure was determined (required; not in the future)
 */
public record FailBookingItemRequest(
        @NotNull(message = "Motivo da falha é obrigatório") BookingFailureReason failureReason,
        @Size(max = 2000, message = "Texto muito longo") String failureNote,
        @NotNull(message = "Data da falha é obrigatória")
                @PastOrPresent(message = "A data da falha não pode ser no futuro")
                Instant failedAt) {}
