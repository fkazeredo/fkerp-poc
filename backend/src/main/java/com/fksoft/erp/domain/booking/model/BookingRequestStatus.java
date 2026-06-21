package com.fksoft.erp.domain.booking.model;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lifecycle status of a {@link BookingRequest} (Sprint 4). A new request starts at {@link #PENDING}; the
 * operational transitions (attempts, confirmation, failure, cancellation) are later slices. Meaning:
 *
 * <ul>
 *   <li>{@link #PENDING} — created, but no operational work started yet;
 *   <li>{@link #IN_PROGRESS} — someone started the reservation work;
 *   <li>{@link #PARTIALLY_CONFIRMED} — some items confirmed, others still pending;
 *   <li>{@link #CONFIRMED} — every item that requires booking is confirmed;
 *   <li>{@link #FAILED} — the reservation failed and needs an operational decision;
 *   <li>{@link #CANCELLED} — the request was cancelled before completion.
 * </ul>
 */
public enum BookingRequestStatus {
    PENDING,
    IN_PROGRESS,
    PARTIALLY_CONFIRMED,
    CONFIRMED,
    FAILED,
    CANCELLED;

    /**
     * Whether the request is still active (counts against the "one active request per Commercial Order"
     * rule). Once it is {@link #CANCELLED}, the Order may originate a new Booking Request.
     *
     * @return {@code true} unless the status is CANCELLED
     */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /**
     * The set of active statuses (everything except CANCELLED) — the default operational set for the
     * one-active-request-per-Order rule.
     *
     * @return the active statuses
     */
    public static Set<BookingRequestStatus> activeStatuses() {
        return Stream.of(values()).filter(BookingRequestStatus::isActive).collect(Collectors.toUnmodifiableSet());
    }
}
