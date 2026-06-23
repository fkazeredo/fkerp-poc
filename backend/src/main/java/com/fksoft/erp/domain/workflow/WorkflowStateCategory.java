package com.fksoft.erp.domain.workflow;

/**
 * Structural classification of a {@link WorkflowState}. This is an engine-internal, structural enum (not a
 * business value list): the engine and queries rely on it to know which states are entry points and which
 * are closed, replacing the per-aggregate {@code isTerminal()}/{@code isOpen()}/{@code isActive()} helpers
 * that used to live on the status enums.
 *
 * <ul>
 *   <li>{@link #INITIAL} — an entry state a new record may start in;
 *   <li>{@link #ACTIVE} — an open, in-progress state;
 *   <li>{@link #TERMINAL_POSITIVE} — a closed-won / successful end state;
 *   <li>{@link #TERMINAL_NEGATIVE} — a closed-lost / unsuccessful end state.
 * </ul>
 */
public enum WorkflowStateCategory {
    INITIAL,
    ACTIVE,
    TERMINAL_POSITIVE,
    TERMINAL_NEGATIVE;

    /**
     * Whether this category is a closed (terminal) outcome.
     *
     * @return {@code true} for the two terminal categories
     */
    public boolean isTerminal() {
        return this == TERMINAL_POSITIVE || this == TERMINAL_NEGATIVE;
    }
}
