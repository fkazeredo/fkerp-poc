package com.fksoft.erp.domain.booking.exception;

import com.fksoft.erp.domain.error.DomainException;
import com.fksoft.erp.domain.error.ErrorDetails;
import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a create request marks an order item as requiring booking that is not eligible — i.e. it is not
 * an OTHER item of the source Order (a travel package / car rental already require booking, a service fee never
 * does, and only an OTHER item may be explicitly marked). The offending item id is carried as a detail.
 */
public class BookingItemNotMarkableException extends DomainException implements ErrorDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient UUID itemId;

    public BookingItemNotMarkableException(UUID itemId) {
        super("booking.item-not-markable");
        this.itemId = itemId;
    }

    @Override
    public Map<String, String> details() {
        return Map.of("itemId", itemId.toString());
    }
}
