package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a booking operator is given on creation but is unknown or inactive. */
public class BookingOperatorNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingOperatorNotFoundException() {
        super("booking.operator-not-found");
    }
}
