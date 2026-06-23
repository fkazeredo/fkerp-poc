package com.fksoft.erp.domain.crm.model;

/**
 * The Lead lifecycle, a fixed state machine with pre-defined transitions:
 *
 * <pre>
 *   NEW --(effective contact)--> CONTACTED --(qualify)--> QUALIFIED
 *   {NEW, CONTACTED, QUALIFIED} --(lose)--> LOST
 * </pre>
 *
 * The legal transitions and their guards live on the {@link Lead} entity (qualify requires CONTACTED + a
 * responsible; lose is allowed from any non-terminal state). {@code LOST} is the only terminal state.
 * Persisted as its name ({@code @Enumerated(STRING)}); the name is also the value exposed in the JSON
 * contract and grouped on by the indicator queries.
 */
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    LOST;

    /** Whether this is the terminal (lost) state — no further transition is allowed. */
    public boolean isTerminal() {
        return this == LOST;
    }
}
