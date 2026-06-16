package com.fksoft.erp.domain.crm.model;

import java.util.Map;

/**
 * Stages of the commercial Opportunity pipeline (Sprint 2). A new Opportunity starts at
 * {@link #NEW_OPPORTUNITY} and advances one step forward along the funnel
 * ({@code NEW_OPPORTUNITY → DISCOVERY → PRODUCT_FIT → READY_FOR_PROPOSAL}); skipping a stage or going
 * back is not allowed. {@link #LOST} is terminal and is reached only through the lose action (not via a
 * stage advance). A {@code READY_FOR_PROPOSAL} Opportunity may originate a Proposal in Sprint 3 (not
 * implemented here).
 */
public enum OpportunityStage {
    NEW_OPPORTUNITY,
    DISCOVERY,
    PRODUCT_FIT,
    READY_FOR_PROPOSAL,
    LOST;

    // The single forward step allowed from each stage (LOST and READY_FOR_PROPOSAL have none).
    private static final Map<OpportunityStage, OpportunityStage> NEXT_STAGE =
            Map.of(NEW_OPPORTUNITY, DISCOVERY, DISCOVERY, PRODUCT_FIT, PRODUCT_FIT, READY_FOR_PROPOSAL);

    /**
     * Whether the pipeline allows advancing from this stage to {@code target}: only the single forward
     * step is permitted, so going back, skipping a stage and moving to {@link #LOST} all return
     * {@code false} (LOST is reached only through the lose action).
     *
     * @param target the destination stage
     * @return {@code true} if {@code target} is the immediate next stage of this one
     */
    public boolean canAdvanceTo(OpportunityStage target) {
        return NEXT_STAGE.get(this) == target;
    }
}
