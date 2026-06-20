package com.fksoft.erp.domain.sales.model;

/**
 * The booking-need filter for the Commercial Order list: whether an Order requires a booking operation. It
 * maps to the Order status — {@link #REQUIRED} ⇒ {@link CommercialOrderStatus#PENDING_BOOKING},
 * {@link #NOT_REQUIRED} ⇒ {@link CommercialOrderStatus#BOOKING_NOT_REQUIRED}.
 */
public enum BookingNeed {
    REQUIRED,
    NOT_REQUIRED;

    /** The Order status this booking-need value corresponds to. */
    public CommercialOrderStatus toStatus() {
        return this == REQUIRED ? CommercialOrderStatus.PENDING_BOOKING : CommercialOrderStatus.BOOKING_NOT_REQUIRED;
    }
}
