package com.fksoft.erp.domain.financial.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Receivable is requested for a Commercial Order whose booking is not CONFIRMED. */
public class OrderBookingNotConfirmedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OrderBookingNotConfirmedException() {
        super("financial.receivable.order-not-confirmed");
    }
}
