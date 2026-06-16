package com.fksoft.erp.domain.crm.model;

/**
 * Types of commercial activity that can be registered on an Opportunity (Sprint 2 — a fixed set). The
 * user-facing labels are resolved in the frontend (like the pipeline stages).
 */
public enum OpportunityActivityType {
    PHONE_CALL,
    WHATSAPP,
    EMAIL,
    MEETING,
    INTERNAL_NOTE,
    DOCUMENT_REQUEST,
    PRICE_DISCUSSION,
    TRAVEL_REQUIREMENT_CLARIFICATION,
    OTHER
}
