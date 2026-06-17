package com.fksoft.erp.domain.crm.model;

/**
 * Reasons an Opportunity can be marked as lost (Sprint 2 — a fixed, commercial set, distinct from the
 * Lead's contact-oriented loss reasons). The user-facing labels are resolved in the frontend (like the
 * pipeline stages and activity types).
 */
public enum OpportunityLossReason {
    NO_BUDGET,
    NO_DECISION,
    NO_RESPONSE,
    COMPETITOR_CHOSEN,
    PRODUCT_MISMATCH,
    PRICE_TOO_HIGH,
    TRAVEL_CANCELLED,
    DUPLICATED_OPPORTUNITY,
    OUT_OF_PROFILE,
    OTHER
}
