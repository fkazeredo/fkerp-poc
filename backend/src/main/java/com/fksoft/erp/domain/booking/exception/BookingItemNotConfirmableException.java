package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a booking item cannot be confirmed through the Travel Package flow — it is not a Travel Package
 * item, or it does not require booking.
 */
public class BookingItemNotConfirmableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BookingItemNotConfirmableException() {
        super("booking.item-not-confirmable");
    }
}
