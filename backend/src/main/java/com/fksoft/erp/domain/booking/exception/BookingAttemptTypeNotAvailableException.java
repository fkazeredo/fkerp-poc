package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when using an unknown or inactive BookingAttemptType. */
public class BookingAttemptTypeNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingAttemptTypeNotAvailableException() {
        super("booking.attempt-type-not-available");
    }
}
