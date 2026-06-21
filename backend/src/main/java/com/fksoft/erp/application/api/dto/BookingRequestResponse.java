package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import java.util.UUID;

/**
 * Response after creating a Booking Request: the new id and its initial status.
 *
 * @param id the new Booking Request id
 * @param status the initial status (PENDING)
 */
public record BookingRequestResponse(UUID id, BookingRequestStatus status) {}
