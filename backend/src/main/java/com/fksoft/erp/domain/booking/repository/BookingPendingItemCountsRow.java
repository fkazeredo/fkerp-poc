package com.fksoft.erp.domain.booking.repository;

import java.util.UUID;

/**
 * Projection of a Booking Request's item counts for the operational pending-items worklist: how many items
 * require booking, how many are confirmed, how many failed, and how many requiring-booking items are still
 * pending. Computed in one grouped query (no N+1) so the pending reasons can be derived without lazy-loading
 * each request's items.
 */
public interface BookingPendingItemCountsRow {

    UUID getBookingRequestId();

    long getRequiring();

    long getConfirmed();

    long getFailed();

    long getPendingRequired();
}
