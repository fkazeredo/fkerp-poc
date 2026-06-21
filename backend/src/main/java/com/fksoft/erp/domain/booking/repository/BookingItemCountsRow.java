package com.fksoft.erp.domain.booking.repository;

import java.util.UUID;

/**
 * Projection of a Booking Request's item counts for the operational list: how many of its items require
 * booking and how many are already confirmed. Computed in one grouped query (no N+1).
 */
public interface BookingItemCountsRow {

    UUID getBookingRequestId();

    long getRequiring();

    long getConfirmed();
}
