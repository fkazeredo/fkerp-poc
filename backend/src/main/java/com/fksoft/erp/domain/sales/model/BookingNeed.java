package com.fksoft.erp.domain.sales.model;

/**
 * The booking-need filter for the Commercial Order list: whether an Order requires a booking operation. It
 * maps to the Order status code — {@link #REQUIRED} ⇒ {@code PENDING_BOOKING},
 * {@link #NOT_REQUIRED} ⇒ {@code BOOKING_NOT_REQUIRED}.
 */
public enum BookingNeed {
    REQUIRED,
    NOT_REQUIRED;

    /** The Order status code this booking-need value corresponds to. */
    public String toStatus() {
        return this == REQUIRED ? "PENDING_BOOKING" : "BOOKING_NOT_REQUIRED";
    }
}
