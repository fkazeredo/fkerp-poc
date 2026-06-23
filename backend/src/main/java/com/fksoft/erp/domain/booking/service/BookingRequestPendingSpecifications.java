package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestPendingReasons;
import com.fksoft.erp.domain.workflow.WorkflowAttentionRule;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Booking Requests that need action — the OR of the active attention rules'
 * conditions (excluding the terminal CONFIRMED / CANCELLED requests). Mirrors
 * {@link BookingRequestPendingReasons#of}; both are driven by the same configurable
 * {@link WorkflowAttentionRule}s of the {@code booking_request} workflow.
 */
public final class BookingRequestPendingSpecifications {

    private BookingRequestPendingSpecifications() {}

    /**
     * A Booking Request is pending (and not terminal) when at least one active attention rule matches.
     *
     * @param now the reference instant (for the staleness window)
     * @param today the reference calendar date (for "overdue" date comparisons)
     * @param rules the active attention rules of the {@code booking_request} workflow
     * @return the pending Specification
     */
    public static Specification<BookingRequest> pending(
            Instant now, LocalDate today, List<WorkflowAttentionRule> rules) {
        return (root, query, cb) -> {
            List<Predicate> ors = new ArrayList<>();
            for (WorkflowAttentionRule rule : rules) {
                Predicate predicate = predicate(rule, root, query, cb, now, today);
                if (predicate != null) {
                    ors.add(predicate);
                }
            }
            Predicate any = ors.isEmpty() ? cb.disjunction() : cb.or(ors.toArray(Predicate[]::new));
            return cb.and(cb.not(root.get("status").in("CONFIRMED", "CANCELLED")), any);
        };
    }

    private static Predicate predicate(
            WorkflowAttentionRule rule,
            Root<BookingRequest> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Instant now,
            LocalDate today) {
        var status = root.get("status");
        return switch (rule.conditionKey()) {
            case "UNASSIGNED_OPERATOR" -> cb.isNull(root.get("bookingOperatorId"));
            case "STATUS_IS" -> cb.equal(status, rule.stateValue());
            case "IN_PROGRESS_STALE" -> {
                Instant staleBefore = now.minus(days(rule), ChronoUnit.DAYS);
                yield cb.and(
                        cb.equal(status, "IN_PROGRESS"),
                        cb.or(
                                cb.isNull(root.get("lastAttemptAt")),
                                cb.lessThan(root.<Instant>get("lastAttemptAt"), staleBefore)));
            }
            case "HAS_FAILED_ITEM" -> hasFailedItem(root, query, cb);
            case "HAS_PENDING_REQUIRED_ITEM" -> hasPendingRequiredItem(root, query, cb);
            case "NEXT_ACTION_OVERDUE" -> cb.and(
                    cb.isNotNull(root.get("nextActionDate")),
                    cb.lessThan(root.<LocalDate>get("nextActionDate"), today));
            default -> null;
        };
    }

    // "Has at least one failed item": an EXISTS over the request's items with status FAILED (correlated subquery,
    // so pagination never duplicates the parent row).
    private static Predicate hasFailedItem(Root<BookingRequest> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<BookingRequest> parent = sub.correlate(root);
        Join<BookingRequest, BookingItem> items = parent.join("items");
        sub.select(cb.literal(1)).where(cb.equal(items.get("status"), "FAILED"));
        return cb.exists(sub);
    }

    // "Has at least one requiring-booking item still pending": an EXISTS over the request's items.
    private static Predicate hasPendingRequiredItem(
            Root<BookingRequest> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<BookingRequest> parent = sub.correlate(root);
        Join<BookingRequest, BookingItem> items = parent.join("items");
        sub.select(cb.literal(1))
                .where(cb.and(cb.isTrue(items.get("requiresBooking")), cb.equal(items.get("status"), "PENDING")));
        return cb.exists(sub);
    }

    private static int days(WorkflowAttentionRule rule) {
        return rule.thresholdDays() == null ? 0 : rule.thresholdDays();
    }
}
