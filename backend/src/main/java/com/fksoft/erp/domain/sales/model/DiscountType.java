package com.fksoft.erp.domain.sales.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a discount is expressed — chosen according to the negotiation. It is used both per Proposal line
 * ({@link ProposalItem}) and at the Proposal level:
 *
 * <ul>
 *   <li>{@link #AMOUNT} — an absolute value (currency) subtracted from a base (must be between 0 and the
 *       base);
 *   <li>{@link #PERCENT} — a percentage (0–100) applied to a base.
 * </ul>
 *
 * <p>The discount math and validation live here so both the item-level and the proposal-level discount
 * reuse the exact same rules (the {@code base} is the line subtotal for an item, or the items subtotal for
 * the whole Proposal).
 */
public enum DiscountType {
    AMOUNT,
    PERCENT;

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * The discount amount this type yields for {@code value} against {@code base}, at scale 2 (HALF_UP).
     *
     * @param value the discount value (an absolute amount for {@link #AMOUNT}, a percentage for {@link #PERCENT})
     * @param base the base the discount applies to (a line subtotal, or the Proposal's items subtotal)
     * @return the absolute discount amount
     */
    public BigDecimal amountOf(BigDecimal value, BigDecimal base) {
        return switch (this) {
            case AMOUNT -> value.setScale(SCALE, RoundingMode.HALF_UP);
            case PERCENT -> base.multiply(value).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        };
    }

    /**
     * Whether {@code value} is a valid discount of this type against {@code base}: a non-negative number that
     * is at most 100 for {@link #PERCENT} and at most the {@code base} for {@link #AMOUNT}.
     *
     * @param value the discount value to check
     * @param base the base the discount applies to
     * @return {@code true} if the value is within the allowed range
     */
    public boolean isValid(BigDecimal value, BigDecimal base) {
        if (value == null || value.signum() < 0) {
            return false;
        }
        return switch (this) {
            case AMOUNT -> value.compareTo(base) <= 0;
            case PERCENT -> value.compareTo(HUNDRED) <= 0;
        };
    }
}
