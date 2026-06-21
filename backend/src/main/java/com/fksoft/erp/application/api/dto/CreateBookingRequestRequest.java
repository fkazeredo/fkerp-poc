package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Request to create a Booking Request from a PENDING_BOOKING Commercial Order.
 *
 * @param commercialOrderId the source Commercial Order id (required)
 * @param bookingOperatorId the assigned booking operator id, or {@code null} (optional initially)
 * @param notes optional booking notes
 * @param bookingRequiredItemIds ids of the Order's OTHER items to mark as requiring booking, or {@code null}
 *     (each must be an OTHER item of the Order; a travel package / car rental always require booking and a
 *     service fee never does, so only OTHER items are markable)
 */
public record CreateBookingRequestRequest(
        @NotNull UUID commercialOrderId,
        UUID bookingOperatorId,
        @Size(max = 2000) String notes,
        Set<UUID> bookingRequiredItemIds) {}
