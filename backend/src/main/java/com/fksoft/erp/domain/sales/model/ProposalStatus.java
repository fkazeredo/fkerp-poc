package com.fksoft.erp.domain.sales.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Proposal lifecycle, a fixed state machine with pre-defined transitions:
 *
 * <pre>
 *   DRAFT --submit--> READY_FOR_REVIEW --approve--> APPROVED --send--> SENT --accept--> ACCEPTED
 *   READY_FOR_REVIEW --reject--> REJECTED ; SENT --decline--> REJECTED
 * </pre>
 *
 * {@code ACCEPTED} is the winning offer that originates the Commercial Order; {@code REJECTED}, {@code EXPIRED}
 * and {@code CANCELLED} are the terminal-negative ("not open") outcomes. The legal transitions and their
 * guards live on the {@link Proposal} entity. Persisted as its name ({@code @Enumerated(STRING)}); the name is
 * the value exposed in the JSON contract and grouped on by the indicator queries.
 */
public enum ProposalStatus {
    DRAFT,
    READY_FOR_REVIEW,
    APPROVED,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED;

    /**
     * Whether the Proposal is still open — i.e. not a terminal-negative outcome (REJECTED/EXPIRED/CANCELLED).
     * An open Proposal blocks a new one for the same Opportunity.
     */
    public boolean isOpen() {
        return this != REJECTED && this != EXPIRED && this != CANCELLED;
    }

    /** The open statuses (for the "at most one open Proposal per Opportunity" query). */
    public static Set<ProposalStatus> open() {
        return EnumSet.of(DRAFT, READY_FOR_REVIEW, APPROVED, SENT, ACCEPTED);
    }
}
