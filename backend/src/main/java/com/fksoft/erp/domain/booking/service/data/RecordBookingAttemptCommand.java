package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingAttemptResult;
import com.fksoft.erp.domain.booking.model.BookingAttemptType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to register a manual booking attempt on a Booking Request.
 *
 * @param bookingItemId the booking item this attempt concerns, or {@code null} for the whole request
 * @param type the attempt type
 * @param result the attempt outcome
 * @param description what was done
 * @param occurredAt when the attempt happened
 * @param nextActionDate optional planned next action date
 */
public record RecordBookingAttemptCommand(
        UUID bookingItemId,
        BookingAttemptType type,
        BookingAttemptResult result,
        String description,
        Instant occurredAt,
        LocalDate nextActionDate) {}
