package com.fksoft.erp.domain.sales.model;

/**
 * Reasons a client can reject a sent Proposal (a fixed commercial set, distinct from the internal-review
 * {@link ProposalRejectionReason}). A Proposal reaches {@link ProposalStatus#REJECTED} either through the
 * internal review (with a {@code ProposalRejectionReason}) or through the client's decision (with a
 * {@code CustomerRejectionReason}); the two paths are mutually exclusive. The user-facing labels are resolved
 * in the frontend (like the proposal statuses and the pipeline stages).
 */
public enum CustomerRejectionReason {
    PRICE_TOO_HIGH,
    CHOSE_COMPETITOR,
    TRAVEL_POSTPONED,
    TRAVEL_CANCELLED,
    CHANGED_DESTINATION,
    NO_RESPONSE,
    PRODUCT_MISMATCH,
    OTHER
}
