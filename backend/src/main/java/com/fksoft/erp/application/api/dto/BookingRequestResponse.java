package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Response after creating a Booking Request: the new id and its initial status code.
 *
 * @param id the new Booking Request id
 * @param status the initial status code (PENDING)
 */
public record BookingRequestResponse(UUID id, String status) {}
