package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.BookingNeed;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters for the operational Commercial Order list (all optional). Spring binds them from the
 * request; the controller maps them to the domain
 * {@link com.fksoft.erp.domain.sales.service.data.CommercialOrderSearchCriteria}.
 *
 * @param status statuses to include (empty ⇒ active; include CANCELLED to see cancelled Orders)
 * @param responsible a responsible user id, or the literal {@code unassigned} for the unassigned pool
 * @param createdFrom inclusive lower bound on the creation date (ISO date)
 * @param createdTo inclusive upper bound on the creation date (ISO date)
 * @param totalMin inclusive lower bound on the total amount
 * @param totalMax inclusive upper bound on the total amount
 * @param bookingNeed restrict by booking need (REQUIRED / NOT_REQUIRED)
 * @param q free-text search over the source Proposal title
 */
public record CommercialOrderListParams(
        Set<CommercialOrderStatus> status,
        String responsible,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        BigDecimal totalMin,
        BigDecimal totalMax,
        BookingNeed bookingNeed,
        String q) {}
