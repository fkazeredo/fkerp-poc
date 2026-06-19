package com.fksoft.erp.domain.sales.model;

/**
 * Reasons a Proposal can be rejected at internal review (a fixed commercial set, like the Opportunity's
 * loss reasons). The user-facing labels are resolved in the frontend (like the proposal statuses and the
 * pipeline stages).
 */
public enum ProposalRejectionReason {
    PRICE_TOO_HIGH,
    DISCOUNT_OUT_OF_POLICY,
    INCOMPLETE_INFORMATION,
    TERMS_NOT_ACCEPTABLE,
    VALIDITY_TOO_SHORT,
    DUPLICATE,
    OTHER
}
