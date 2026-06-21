package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when the caller is not allowed to see a Booking Request. */
public class BookingRequestAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingRequestAccessDeniedException() {
        super("booking.access-denied");
    }
}
