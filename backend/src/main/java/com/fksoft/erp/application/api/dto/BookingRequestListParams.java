package com.fksoft.erp.application.api.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters for the operational Booking Request list (all optional). Spring binds them from the
 * request; the controller maps them to the domain
 * {@link com.fksoft.erp.domain.booking.service.data.BookingRequestSearchCriteria}.
 *
 * @param status statuses to include (empty ⇒ active operational set; include CONFIRMED/CANCELLED to see those)
 * @param operator a booking-operator user id, or the literal {@code unassigned} for the no-operator pool
 * @param responsible a commercial-responsible user id
 * @param createdFrom inclusive lower bound on the creation date (ISO date)
 * @param createdTo inclusive upper bound on the creation date (ISO date)
 * @param order restrict to a single source Commercial Order id
 * @param itemType restrict to requests that contain an item of this type (the item-type code)
 * @param hasFailedItems restrict to requests that contain at least one failed item
 */
public record BookingRequestListParams(
        Set<String> status,
        String operator,
        UUID responsible,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        UUID order,
        String itemType,
        Boolean hasFailedItems) {}
