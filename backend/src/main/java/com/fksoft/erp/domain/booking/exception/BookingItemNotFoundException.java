package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a referenced booking item does not belong to the Booking Request. */
public class BookingItemNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingItemNotFoundException() {
        super("booking.item-not-found");
    }
}
