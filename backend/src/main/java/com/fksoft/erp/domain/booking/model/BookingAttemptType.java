package com.fksoft.erp.domain.booking.model;

/**
 * The kind of manual booking attempt registered on a {@link BookingRequest} (Sprint 4). A fixed operational
 * set (enum + DB CHECK, not an editable cadastro). It records <b>what the operator did</b> while working the
 * reservation — it is not an external integration and never confirms a booking.
 */
public enum BookingAttemptType {
    EXTERNAL_SYSTEM_ACCESS,
    SUPPLIER_PHONE_CONTACT,
    SUPPLIER_EMAIL_CONTACT,
    INTERNAL_VERIFICATION,
    MANUAL_AVAILABILITY_CHECK,
    OTHER
}
