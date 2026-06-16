package com.fksoft.erp.domain.crm.model;

/**
 * Outcomes of a commercial activity registered on an Opportunity (Sprint 2 — a fixed set). A result is
 * informative: it never moves the pipeline stage on its own (that is the explicit advance action). The
 * user-facing labels are resolved in the frontend (like the pipeline stages).
 */
public enum OpportunityActivityResult {
    CLIENT_ENGAGED,
    NEEDS_FOLLOW_UP,
    WAITING_FOR_CLIENT,
    WAITING_FOR_INTERNAL_INFO,
    PRODUCT_FIT_IDENTIFIED,
    READY_FOR_PROPOSAL,
    NOT_INTERESTED,
    OTHER
}
