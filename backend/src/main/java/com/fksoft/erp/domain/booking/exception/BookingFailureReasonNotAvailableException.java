package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when using an unknown or inactive BookingFailureReason. */
public class BookingFailureReasonNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingFailureReasonNotAvailableException() {
        super("booking.failure-reason-not-available");
    }
}
