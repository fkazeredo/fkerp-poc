package com.fksoft.erp.domain.sales.service.data;

import com.fksoft.erp.domain.sales.model.BookingNeed;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for the operational Commercial Order list (all optional). Empty {@code statuses} means the
 * default operational view, which excludes the inactive CANCELLED Orders; include it explicitly to see
 * cancelled Orders.
 *
 * @param statuses statuses to include (empty/null ⇒ active, i.e. excluding CANCELLED)
 * @param responsibleId restrict to this responsible user
 * @param unassignedOnly restrict to Orders with no responsible (takes precedence over responsibleId)
 * @param createdFrom inclusive lower bound on the creation instant
 * @param createdTo exclusive upper bound on the creation instant
 * @param totalMin inclusive lower bound on the total amount
 * @param totalMax inclusive upper bound on the total amount
 * @param bookingNeed restrict by booking need (maps to the PENDING_BOOKING / BOOKING_NOT_REQUIRED status)
 * @param query free-text search over the source Proposal title
 */
public record CommercialOrderSearchCriteria(
        Set<CommercialOrderStatus> statuses,
        UUID responsibleId,
        boolean unassignedOnly,
        Instant createdFrom,
        Instant createdTo,
        BigDecimal totalMin,
        BigDecimal totalMax,
        BookingNeed bookingNeed,
        String query) {}
