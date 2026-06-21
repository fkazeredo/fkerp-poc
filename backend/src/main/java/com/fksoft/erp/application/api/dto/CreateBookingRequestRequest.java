package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request to create a Booking Request from a PENDING_BOOKING Commercial Order.
 *
 * @param commercialOrderId the source Commercial Order id (required)
 * @param bookingOperatorId the assigned booking operator id, or {@code null} (optional initially)
 * @param notes optional booking notes
 */
public record CreateBookingRequestRequest(
        @NotNull UUID commercialOrderId, UUID bookingOperatorId, @Size(max = 2000) String notes) {}
