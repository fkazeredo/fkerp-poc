package com.fksoft.erp.domain.booking.model;

/**
 * The reason a {@link BookingItem} was manually marked as failed (Sprint 4). A fixed operational set (enum + DB
 * CHECK, not an editable cadastro); the user-facing labels are resolved in the frontend. A failure is recorded
 * for the operational decision — it never cancels the Commercial Order and creates no Financial/Commission/
 * Customer Care data.
 */
public enum BookingFailureReason {
    NO_AVAILABILITY,
    SUPPLIER_UNAVAILABLE,
    INVALID_COMMERCIAL_DATA,
    MISSING_TRAVELER_DATA,
    EXTERNAL_SYSTEM_UNAVAILABLE,
    PRICE_CHANGED,
    MANUAL_OPERATION_ERROR,
    OUT_OF_POLICY,
    OTHER
}
