package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a booking item is already confirmed or cancelled and so cannot be confirmed again. */
public class BookingItemAlreadyResolvedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingItemAlreadyResolvedException() {
        super("booking.item-already-resolved");
    }
}
