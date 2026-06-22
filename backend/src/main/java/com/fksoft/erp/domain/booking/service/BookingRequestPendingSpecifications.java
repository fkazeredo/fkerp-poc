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
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Booking Requests that need action — the OR of the pending categories (Slice 10).
 * Mirrors {@link BookingRequestPendingReasons#of} so the page contains exactly the requests that have at least
 * one reason. Terminal CONFIRMED / CANCELLED requests are always excluded.
 */
public final class BookingRequestPendingSpecifications {

    private BookingRequestPendingSpecifications() {}

    /**
     * A Booking Request is pending (and not terminal) when it has no booking operator, is still PENDING, is IN
     * PROGRESS with no attempt within the staleness window, has a failed item, has a requiring-booking item still
     * pending, is PARTIALLY_CONFIRMED, or its planned next action is overdue. Mirrors
     * {@link BookingRequestPendingReasons#of}.
     *
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @return the pending Specification
     */
    public static Specification<BookingRequest> pending(Instant now, LocalDate today) {
        return (root, query, cb) -> {
            var status = root.get("status");
            Instant staleBefore = now.minus(BookingRequestPendingReasons.STALE_DAYS, ChronoUnit.DAYS);

            var unassignedOperator = cb.isNull(root.get("bookingOperatorId"));
            var pendingWithoutAttempt = cb.equal(status, BookingRequestStatus.PENDING);
            var inProgressStale = cb.and(
                    cb.equal(status, BookingRequestStatus.IN_PROGRESS),
                    cb.or(
                            cb.isNull(root.get("lastAttemptAt")),
                            cb.lessThan(root.<Instant>get("lastAttemptAt"), staleBefore)));
            var partiallyConfirmed = cb.equal(status, BookingRequestStatus.PARTIALLY_CONFIRMED);
            var overdueNextAction = cb.and(
                    cb.isNotNull(root.get("nextActionDate")),
                    cb.lessThan(root.<LocalDate>get("nextActionDate"), today));

            return cb.and(
                    cb.not(status.in(BookingRequestStatus.CONFIRMED, BookingRequestStatus.CANCELLED)),
                    cb.or(
                            unassignedOperator,
                            pendingWithoutAttempt,
                            inProgressStale,
                            hasFailedItem(root, query, cb),
                            hasPendingRequiredItem(root, query, cb),
                            partiallyConfirmed,
                            overdueNextAction));
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
