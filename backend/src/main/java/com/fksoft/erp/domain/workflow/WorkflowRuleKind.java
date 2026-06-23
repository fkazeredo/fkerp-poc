package com.fksoft.erp.domain.workflow;

/**
 * The role a {@link WorkflowRule} plays on its transition, mirroring the mainstream workflow model (Jira):
 *
 * <ul>
 *   <li>{@link #CONDITION} — gate on who/when may fire the transition (runs before mutation, blocks);
 *   <li>{@link #VALIDATOR} — input/business check on the payload (runs before mutation, blocks);
 *   <li>{@link #POST_FUNCTION} — side effect to run after the state changes (history, enrichment, events).
 * </ul>
 *
 * Conditions and validators are both <em>guards</em> (resolved against a {@link WorkflowGuard} bean and run
 * before the state mutation); post functions are resolved against a {@link WorkflowPostFunction} bean and run
 * after. The split is kept for the authoring catalog and labels.
 */
public enum WorkflowRuleKind {
    CONDITION,
    VALIDATOR,
    POST_FUNCTION;

    /**
     * Whether this rule runs in the pre-mutation guard phase (condition or validator).
     *
     * @return {@code true} for CONDITION and VALIDATOR
     */
    public boolean isGuard() {
        return this == CONDITION || this == VALIDATOR;
    }
}
