package com.fksoft.erp.domain.commission.model;

/**
 * The commercial actor a {@link CommissionRule} targets — the kind of responsible person who earns the
 * commission. A rule may apply to all actors of a type or, when a specific {@code targetUserId} is set, to a
 * single user of that type. Persisted as its name ({@code @Enumerated(STRING)}, mirrored by a DB {@code CHECK});
 * the name is the value in the JSON contract.
 */
public enum CommissionTargetType {
    /** A seller (inside sales). */
    SELLER,
    /** A sales representative (external). */
    SALES_REPRESENTATIVE,
    /** The commercial responsible person of the deal (generic). */
    COMMERCIAL_RESPONSIBLE
}
