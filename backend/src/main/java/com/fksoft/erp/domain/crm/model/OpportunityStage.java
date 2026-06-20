package com.fksoft.erp.domain.crm.model;

import java.util.Map;
import java.util.Set;

/**
 * Stages of the commercial Opportunity pipeline (Sprint 2+). A new Opportunity starts at
 * {@link #NEW_OPPORTUNITY} and advances one step forward along the funnel
 * ({@code NEW_OPPORTUNITY → DISCOVERY → PRODUCT_FIT → READY_FOR_PROPOSAL}); skipping a stage or going
 * back is not allowed. {@link #WON} and {@link #LOST} are terminal: {@link #LOST} is reached only through
 * the lose action, and {@link #WON} only when a Commercial Order is created from an Accepted Proposal
 * (Sprint 3); neither is reachable through a stage advance. A {@code READY_FOR_PROPOSAL} Opportunity may
 * originate a Proposal.
 */
public enum OpportunityStage {
    NEW_OPPORTUNITY,
    DISCOVERY,
    PRODUCT_FIT,
    READY_FOR_PROPOSAL,
    WON,
    LOST;

    // The single forward step allowed from each stage (the terminal stages and READY_FOR_PROPOSAL have none).
    private static final Map<OpportunityStage, OpportunityStage> NEXT_STAGE =
            Map.of(NEW_OPPORTUNITY, DISCOVERY, DISCOVERY, PRODUCT_FIT, PRODUCT_FIT, READY_FOR_PROPOSAL);

    private static final Set<OpportunityStage> TERMINAL = Set.of(WON, LOST);

    /**
     * Whether the pipeline allows advancing from this stage to {@code target}: only the single forward
     * step is permitted, so going back, skipping a stage and moving to a terminal stage all return
     * {@code false} ({@link #LOST} is reached only through the lose action, {@link #WON} only via order
     * creation).
     *
     * @param target the destination stage
     * @return {@code true} if {@code target} is the immediate next stage of this one
     */
    public boolean canAdvanceTo(OpportunityStage target) {
        return NEXT_STAGE.get(this) == target;
    }

    /**
     * Whether this is a terminal (closed) stage — {@link #WON} or {@link #LOST}. Terminal Opportunities are
     * excluded from the active pipeline, the default operational list and the pending worklist.
     *
     * @return {@code true} if the stage is WON or LOST
     */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * The terminal (closed) stages — WON and LOST — used to exclude closed Opportunities from active views.
     *
     * @return an immutable set of the terminal stages
     */
    public static Set<OpportunityStage> terminalStages() {
        return TERMINAL;
    }
}
