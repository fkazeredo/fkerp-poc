package com.fksoft.erp.domain.booking.service.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to register a manual booking attempt on a Booking Request.
 *
 * @param bookingItemId the booking item this attempt concerns, or {@code null} for the whole request
 * @param typeId the attempt-type cadastro id
 * @param resultId the attempt-result cadastro id
 * @param description what was done
 * @param occurredAt when the attempt happened
 * @param nextActionDate optional planned next action date
 */
public record RecordBookingAttemptCommand(
        UUID bookingItemId,
        UUID typeId,
        UUID resultId,
        String description,
        Instant occurredAt,
        LocalDate nextActionDate) {}
