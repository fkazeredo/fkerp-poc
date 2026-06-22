package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a booking item cannot be marked as failed — it does not require booking. */
public class BookingItemNotFailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingItemNotFailableException() {
        super("booking.item-not-failable");
    }
}
