package com.fksoft.erp.domain.crm.model;

/**
 * Why an Opportunity appears in the operational pending-items worklist. Exposed in the API as a stable
 * code; the frontend localizes it (raw enum names are never shown to users). LOST Opportunities are
 * terminal and never pending.
 */
public enum OpportunityPendingReason {
    /** No commercial activity in the staleness window — the negotiation has gone quiet. */
    WITHOUT_RECENT_ACTIVITY,
    /** A planned next action is past due. */
    OVERDUE_NEXT_ACTION,
    /** Still in NEW_OPPORTUNITY past the staleness window — never started moving. */
    STUCK_IN_NEW,
    /** Still in DISCOVERY past the staleness window — discovery is dragging. */
    STUCK_IN_DISCOVERY,
    /** Ready for a proposal but the proposal step is not yet handled (future sprint). */
    READY_FOR_PROPOSAL,
    /** The expected closing date has passed without an outcome. */
    EXPECTED_CLOSE_OVERDUE
}
