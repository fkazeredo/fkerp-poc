package com.fksoft.erp.domain.booking.repository;

import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Command/read repository for {@link BookingRequest} aggregates (Booking Operations). */
public interface BookingRequestRepository
        extends JpaRepository<BookingRequest, UUID>, JpaSpecificationExecutor<BookingRequest> {

    /**
     * Finds the first Booking Request for a Commercial Order whose status is in the given set — used to
     * enforce the "at most one active request per Order" rule.
     *
     * @param commercialOrderId the source Commercial Order id
     * @param statuses the statuses considered active
     * @return the matching request, if any
     */
    Optional<BookingRequest> findFirstByCommercialOrderIdAndStatusIn(
            UUID commercialOrderId, Collection<BookingRequestStatus> statuses);

    /**
     * Item counts (how many require booking, how many are confirmed) per Booking Request, for the operational
     * list. One grouped query over the given ids (no N+1).
     *
     * @param bookingRequestIds the Booking Request ids to count items for
     * @return one row per request that has items, with the requiring/confirmed counts
     */
    @Query(
            value =
                    """
                    SELECT i.booking_request_id AS bookingRequestId,
                           COUNT(*) FILTER (WHERE i.requires_booking)        AS requiring,
                           COUNT(*) FILTER (WHERE i.status = 'CONFIRMED')    AS confirmed
                    FROM booking_items i
                    WHERE i.booking_request_id IN (:bookingRequestIds)
                    GROUP BY i.booking_request_id
                    """,
            nativeQuery = true)
    List<BookingItemCountsRow> findItemCounts(@Param("bookingRequestIds") Collection<UUID> bookingRequestIds);
}
