package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.booking.model.BookingAttemptResult;
import com.fksoft.erp.domain.booking.model.BookingAttemptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to register a manual booking attempt on a Booking Request.
 *
 * @param bookingItemId the booking item this attempt concerns, or {@code null} for the whole request
 * @param type the attempt type (required; an unknown value is rejected as a 400)
 * @param result the attempt outcome (required)
 * @param description what was done (required)
 * @param occurredAt when the attempt happened (required; not in the future)
 * @param nextActionDate optional planned next action date
 */
public record RegisterBookingAttemptRequest(
        UUID bookingItemId,
        @NotNull(message = "Tipo da tentativa é obrigatório") BookingAttemptType type,
        @NotNull(message = "Resultado da tentativa é obrigatório") BookingAttemptResult result,
        @NotBlank(message = "Descrição é obrigatória") @Size(max = 4000, message = "Descrição muito longa")
                String description,
        @NotNull(message = "Data da tentativa é obrigatória")
                @PastOrPresent(message = "A data da tentativa não pode ser no futuro")
                Instant occurredAt,
        LocalDate nextActionDate) {}
