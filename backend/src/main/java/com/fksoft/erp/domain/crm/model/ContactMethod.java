package com.fksoft.erp.domain.crm.model;

/**
 * The Customer's preferred contact channel. A closed, structural set: each value maps to a contact field the
 * Customer holds ({@code EMAIL} → email, {@code PHONE} → phone, {@code WHATSAPP} → whatsapp), so it is an enum
 * rather than admin-editable reference data (a new channel would require a new field + code). Persisted as its
 * name ({@code @Enumerated(STRING)}, mirrored by a DB {@code CHECK}).
 */
public enum ContactMethod {
    EMAIL,
    PHONE,
    WHATSAPP;
}
