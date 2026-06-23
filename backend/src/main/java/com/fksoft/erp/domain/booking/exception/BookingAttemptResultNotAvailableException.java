package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when using an unknown or inactive BookingAttemptResult. */
public class BookingAttemptResultNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingAttemptResultNotAvailableException() {
        super("booking.attempt-result-not-available");
    }
}
