package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a Commercial Order already has an active Booking Request (at most one active request per
 * Order). The existing request id is carried as a detail so the caller can open it instead of creating
 * another.
 */
public class BookingRequestAlreadyExistsException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID existingBookingRequestId;

    public BookingRequestAlreadyExistsException(UUID existingBookingRequestId) {
        super("booking.already-exists");
        this.existingBookingRequestId = existingBookingRequestId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("bookingRequestId", existingBookingRequestId.toString());
    }
}
