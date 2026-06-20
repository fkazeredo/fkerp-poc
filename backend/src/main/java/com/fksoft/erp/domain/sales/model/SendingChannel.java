package com.fksoft.erp.domain.sales.model;

/**
 * The channel through which an approved Proposal was sent or presented to the client (a fixed, descriptive
 * commercial set, like the Proposal's rejection reasons). It is informational only — marking a Proposal as
 * sent does NOT trigger any real e-mail/WhatsApp/phone integration, and creates no customer acceptance,
 * Commercial Order, Booking, Financial or Commission data. The user-facing labels are resolved in the
 * frontend (like the proposal statuses and the pipeline stages).
 */
public enum SendingChannel {
    EMAIL,
    WHATSAPP,
    PHONE_PRESENTATION,
    IN_PERSON_PRESENTATION,
    OTHER
}
