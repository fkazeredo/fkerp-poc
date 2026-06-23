package com.fksoft.erp.domain.booking.service.data;

import java.time.Instant;
import java.util.UUID;

/**
 * Input to mark a booking item as failed.
 *
 * @param failureReasonId the failure-reason cadastro id
 * @param failureNote an optional note
 * @param failedAt when the failure was determined (operator-supplied)
 */
public record FailBookingItemCommand(UUID failureReasonId, String failureNote, Instant failedAt) {}
