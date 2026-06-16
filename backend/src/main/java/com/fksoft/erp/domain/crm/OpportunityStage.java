package com.fksoft.erp.domain.crm;

/**
 * Stages of the commercial Opportunity pipeline (Sprint 2). A new Opportunity starts at
 * {@link #NEW_OPPORTUNITY}; the allowed transitions (Discovery → Product Fit → Ready for Proposal, or
 * Lost) are implemented in later slices. A {@code READY_FOR_PROPOSAL} Opportunity may originate a
 * Proposal in Sprint 3 (not implemented here).
 */
public enum OpportunityStage {
    NEW_OPPORTUNITY,
    DISCOVERY,
    PRODUCT_FIT,
    READY_FOR_PROPOSAL,
    LOST
}
