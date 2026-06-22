package com.fksoft.erp.domain.booking.model;

/**
 * The outcome of a manual booking attempt on a {@link BookingRequest} (Sprint 4). A fixed operational set
 * (enum + DB CHECK). It is recorded for the operational history only: an attempt result — including
 * {@link #FAILED} — never changes a booking item's status nor confirms/fails the reservation on its own (the
 * confirmation/failure transitions are later slices).
 */
public enum BookingAttemptResult {
    STARTED,
    WAITING_FOR_SUPPLIER,
    WAITING_FOR_INTERNAL_INFO,
    AVAILABILITY_FOUND,
    AVAILABILITY_NOT_FOUND,
    NEEDS_RETRY,
    FAILED,
    OTHER
}
