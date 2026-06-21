package com.fksoft.erp.domain.booking.repository;

import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
