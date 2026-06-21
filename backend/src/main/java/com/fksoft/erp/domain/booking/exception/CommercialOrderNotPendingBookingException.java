package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Booking Request is created from a Commercial Order that is not PENDING_BOOKING. */
public class CommercialOrderNotPendingBookingException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommercialOrderNotPendingBookingException() {
        super("booking.order-not-pending");
    }
}
