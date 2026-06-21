package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Booking Request does not exist. */
public class BookingRequestNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingRequestNotFoundException() {
        super("booking.not-found");
    }
}
