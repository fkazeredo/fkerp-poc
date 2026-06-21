package com.fksoft.erp.domain.booking.model;

/**
 * Status of a {@link BookingItem} within a {@link BookingRequest} (Sprint 4). Items that require booking
 * (a travel package or a car rental) start {@link #PENDING}; items that do not (e.g. a service fee) start
 * {@link #NOT_REQUIRED}. The operational transitions (in progress, confirmed, failed, cancelled) are later
 * slices.
 */
public enum BookingItemStatus {
    PENDING,
    IN_PROGRESS,
    CONFIRMED,
    FAILED,
    NOT_REQUIRED,
    CANCELLED
}
