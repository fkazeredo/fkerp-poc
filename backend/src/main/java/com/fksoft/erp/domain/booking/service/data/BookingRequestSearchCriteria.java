package com.fksoft.erp.domain.booking.service.data;

import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.sales.model.ProposalItemType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for the operational Booking Request list (all optional). Empty {@code statuses} means the
 * default operational view, which excludes the terminal CONFIRMED and CANCELLED requests but keeps FAILED
 * visible (it still needs an operational decision); include a status explicitly to see those too.
 *
 * @param statuses statuses to include (empty/null ⇒ active operational set, i.e. excluding CONFIRMED + CANCELLED)
 * @param bookingOperatorId restrict to this booking operator
 * @param operatorUnassignedOnly restrict to requests with no booking operator (takes precedence over the id)
 * @param responsiblePersonId restrict to this commercial responsible
 * @param createdFrom inclusive lower bound on the creation instant
 * @param createdTo exclusive upper bound on the creation instant
 * @param commercialOrderId restrict to a single source Commercial Order
 * @param itemType restrict to requests that contain an item of this type
 * @param hasFailedItems restrict to requests that contain at least one failed item ({@code null} ⇒ no filter)
 */
public record BookingRequestSearchCriteria(
        Set<BookingRequestStatus> statuses,
        UUID bookingOperatorId,
        boolean operatorUnassignedOnly,
        UUID responsiblePersonId,
        Instant createdFrom,
        Instant createdTo,
        UUID commercialOrderId,
        ProposalItemType itemType,
        Boolean hasFailedItems) {}
