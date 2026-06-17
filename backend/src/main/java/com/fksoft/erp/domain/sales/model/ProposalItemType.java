package com.fksoft.erp.domain.sales.model;

/**
 * Type of a {@link ProposalItem} (Sprint 3). A fixed set for this slice — these classify what the line
 * represents in the commercial offer; they do NOT create a Booking, check availability or compute supplier
 * cost / margin / tax.
 */
public enum ProposalItemType {
    /** A travel package line. */
    TRAVEL_PACKAGE,
    /** A car rental line. */
    CAR_RENTAL,
    /** A service fee line. */
    SERVICE_FEE,
    /** Anything else. */
    OTHER
}
