package com.fksoft.erp.domain.crm.model;

import java.util.Optional;

/**
 * The Opportunity pipeline, a fixed state machine with pre-defined transitions:
 *
 * <pre>
 *   NEW_OPPORTUNITY --advance--> DISCOVERY --advance--> PRODUCT_FIT --advance--> READY_FOR_PROPOSAL
 *   {any non-terminal} --lose--> LOST          (the lose action)
 *   {any non-terminal} --win--> WON            (when a Commercial Order is created from an accepted Proposal)
 * </pre>
 *
 * The strict forward funnel is encoded by {@link #next()} (one step at a time; skipping a stage or going back
 * is impossible). {@code WON} and {@code LOST} are terminal. Persisted as its name ({@code @Enumerated(STRING)});
 * the name is the value exposed in the JSON contract and grouped on by the indicator queries.
 */
public enum OpportunityStage {
    NEW_OPPORTUNITY,
    DISCOVERY,
    PRODUCT_FIT,
    READY_FOR_PROPOSAL,
    WON,
    LOST;

    /** Whether this is a terminal stage (won or lost) — no further transition is allowed. */
    public boolean isTerminal() {
        return this == WON || this == LOST;
    }

    /** Whether the Opportunity is ready to originate a commercial Proposal. */
    public boolean isReadyForProposal() {
        return this == READY_FOR_PROPOSAL;
    }

    /**
     * The next stage in the strict forward funnel, or empty when this stage cannot be advanced (the last
     * active stage and the terminal stages).
     *
     * @return the immediate next stage, or empty
     */
    public Optional<OpportunityStage> next() {
        return switch (this) {
            case NEW_OPPORTUNITY -> Optional.of(DISCOVERY);
            case DISCOVERY -> Optional.of(PRODUCT_FIT);
            case PRODUCT_FIT -> Optional.of(READY_FOR_PROPOSAL);
            default -> Optional.empty();
        };
    }
}
