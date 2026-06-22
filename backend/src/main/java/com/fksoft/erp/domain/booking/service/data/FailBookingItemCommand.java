package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingFailureReason;
import java.time.Instant;

/**
 * Input to mark a booking item as failed.
 *
 * @param failureReason the failure reason (required)
 * @param failureNote an optional note
 * @param failedAt when the failure was determined (operator-supplied)
 */
public record FailBookingItemCommand(BookingFailureReason failureReason, String failureNote, Instant failedAt) {}
