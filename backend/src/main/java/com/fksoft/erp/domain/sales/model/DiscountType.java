package com.fksoft.erp.domain.sales.model;

/**
 * How a {@link ProposalItem}'s optional discount is expressed — chosen per line according to the
 * negotiation:
 *
 * <ul>
 *   <li>{@link #AMOUNT} — an absolute value (currency) subtracted from the line subtotal (must be between
 *       0 and the subtotal);
 *   <li>{@link #PERCENT} — a percentage (0–100) applied to the line subtotal.
 * </ul>
 */
public enum DiscountType {
    AMOUNT,
    PERCENT
}
