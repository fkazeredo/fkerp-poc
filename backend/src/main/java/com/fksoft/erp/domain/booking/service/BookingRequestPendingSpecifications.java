package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingItemStatus;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestPendingReasons;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Booking Requests that need action — the OR of the fixed pre-defined pending
 * conditions (excluding the terminal CONFIRMED / CANCELLED requests). Mirrors
 * {@link BookingRequestPendingReasons#of} so the page contains exactly the requests that have at least one
 * reason.
 */
public final class BookingRequestPendingSpecifications {

    private BookingRequestPendingSpecifications() {}

    /**
     * A Booking Request is pending (and not terminal) when at least one of the pre-defined reasons applies.
     *
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @return the pending Specification
     */
    public static Specification<BookingRequest> pending(Instant now, LocalDate today) {
        return (root, query, cb) -> {
            var status = root.get("status");

            Predicate unassignedOperator = cb.isNull(root.get("bookingOperatorId"));
            Predicate pendingWithoutAttempt = cb.equal(status, BookingRequestStatus.PENDING);

            Instant staleBefore = now.minus(BookingRequestPendingReasons.STALENESS_DAYS, ChronoUnit.DAYS);
            Predicate inProgressStale = cb.and(
                    cb.equal(status, BookingRequestStatus.IN_PROGRESS),
                    cb.or(
                            cb.isNull(root.get("lastAttemptAt")),
                            cb.lessThan(root.<Instant>get("lastAttemptAt"), staleBefore)));

            Predicate hasFailedItem = hasFailedItem(root, query, cb);
            Predicate hasPendingRequiredItem = hasPendingRequiredItem(root, query, cb);
            Predicate partiallyConfirmed = cb.equal(status, BookingRequestStatus.PARTIALLY_CONFIRMED);
            Predicate nextActionOverdue = cb.and(
                    cb.isNotNull(root.get("nextActionDate")),
                    cb.lessThan(root.<LocalDate>get("nextActionDate"), today));

            Predicate any = cb.or(
                    unassignedOperator,
                    pendingWithoutAttempt,
                    inProgressStale,
                    hasFailedItem,
                    hasPendingRequiredItem,
                    partiallyConfirmed,
                    nextActionOverdue);
            return cb.and(
                    cb.not(root.get("status")
                            .in(List.of(BookingRequestStatus.CONFIRMED, BookingRequestStatus.CANCELLED))),
                    any);
        };
    }

    // "Has at least one failed item": an EXISTS over the request's items with status FAILED (correlated subquery,
    // so pagination never duplicates the parent row).
    private static Predicate hasFailedItem(Root<BookingRequest> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<BookingRequest> parent = sub.correlate(root);
        Join<BookingRequest, BookingItem> items = parent.join("items");
        sub.select(cb.literal(1)).where(cb.equal(items.get("status"), BookingItemStatus.FAILED));
        return cb.exists(sub);
    }

    // "Has at least one requiring-booking item still pending": an EXISTS over the request's items.
    private static Predicate hasPendingRequiredItem(
            Root<BookingRequest> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<BookingRequest> parent = sub.correlate(root);
        Join<BookingRequest, BookingItem> items = parent.join("items");
        sub.select(cb.literal(1))
                .where(cb.and(
                        cb.isTrue(items.get("requiresBooking")),
                        cb.equal(items.get("status"), BookingItemStatus.PENDING)));
        return cb.exists(sub);
    }
}
