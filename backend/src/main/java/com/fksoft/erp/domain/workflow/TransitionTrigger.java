package com.fksoft.erp.domain.workflow;

/**
 * How a {@link WorkflowTransition} is fired. Structural (engine-internal), not a business value list.
 *
 * <ul>
 *   <li>{@link #USER} — fired by an explicit user action (e.g. qualify, advance stage, submit);
 *   <li>{@link #SYSTEM} — fired internally by the application (e.g. the booking status consolidation
 *       rolled up from its items, or a Lead auto-moving to CONTACTED on an effective contact).
 * </ul>
 */
public enum TransitionTrigger {
    USER,
    SYSTEM
}
