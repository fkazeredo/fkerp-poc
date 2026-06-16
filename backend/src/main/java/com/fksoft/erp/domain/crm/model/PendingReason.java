package com.fksoft.erp.domain.crm.model;

/**
 * Why a Lead appears in the operational pending-items worklist. Exposed in the API as a stable code;
 * the frontend localizes it (raw enum names are never shown to users).
 */
public enum PendingReason {
    /** No responsible person — the Lead needs an owner. */
    UNASSIGNED,
    /** A NEW Lead with no interaction yet — it was never touched. */
    NEW_WITHOUT_INTERACTION,
    /** A planned next contact is past due. */
    OVERDUE_NEXT_CONTACT,
    /** Contacted but with no follow-up scheduled and not yet qualified or lost. */
    CONTACTED_WITHOUT_OUTCOME
}
