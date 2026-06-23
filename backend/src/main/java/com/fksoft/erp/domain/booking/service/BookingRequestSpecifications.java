package com.fksoft.erp.domain.booking.service;

import com.fksoft.erp.domain.booking.model.BookingItem;
import com.fksoft.erp.domain.booking.model.BookingItemStatus;
import com.fksoft.erp.domain.booking.model.BookingRequest;
import com.fksoft.erp.domain.booking.model.BookingRequestStatus;
import com.fksoft.erp.domain.booking.service.data.BookingRequestSearchCriteria;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Booking Request list. */
public final class BookingRequestSpecifications {

    // The default operational statuses: everything that still needs attention. The terminal CONFIRMED and
    // CANCELLED requests are hidden by default; FAILED stays visible (it needs an operational decision).
    private static final Set<BookingRequestStatus> DEFAULT_STATUSES = Set.of(
            BookingRequestStatus.PENDING,
            BookingRequestStatus.IN_PROGRESS,
            BookingRequestStatus.PARTIALLY_CONFIRMED,
            BookingRequestStatus.FAILED);

    private BookingRequestSpecifications() {}

    /**
     * Combines all filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<BookingRequest> matching(BookingRequestSearchCriteria c) {
        return Specification.allOf(
                statusFilter(c.statuses()),
                operatorFilter(c.bookingOperatorId(), c.operatorUnassignedOnly()),
                responsibleFilter(c.responsiblePersonId()),
                createdRange(c.createdFrom(), c.createdTo()),
                orderFilter(c.commercialOrderId()),
                itemTypeFilter(c.itemType()),
                failedItemsFilter(c.hasFailedItems()));
    }

    // Terminal CONFIRMED/CANCELLED requests never appear by default; pass them in the status filter to see them.
    private static Specification<BookingRequest> statusFilter(Set<String> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return root.get("status").in(DEFAULT_STATUSES);
            }
            Set<BookingRequestStatus> parsed = statuses.stream()
                    .map(BookingRequestSpecifications::toStatus)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return parsed.isEmpty() ? cb.disjunction() : root.get("status").in(parsed);
        };
    }

    private static BookingRequestStatus toStatus(String value) {
        try {
            return BookingRequestStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Specification<BookingRequest> operatorFilter(java.util.UUID operatorId, boolean unassignedOnly) {
        return (root, query, cb) -> {
            if (unassignedOnly) {
                return cb.isNull(root.get("bookingOperatorId"));
            }
            return operatorId == null ? cb.conjunction() : cb.equal(root.get("bookingOperatorId"), operatorId);
        };
    }

    private static Specification<BookingRequest> responsibleFilter(java.util.UUID responsibleId) {
        return (root, query, cb) ->
                responsibleId == null ? cb.conjunction() : cb.equal(root.get("responsiblePersonId"), responsibleId);
    }

    private static Specification<BookingRequest> createdRange(Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<BookingRequest> orderFilter(java.util.UUID commercialOrderId) {
        return (root, query, cb) -> commercialOrderId == null
                ? cb.conjunction()
                : cb.equal(root.get("commercialOrderId"), commercialOrderId);
    }

    // "Contains an item of this type": an EXISTS over the request's items, correlated to the parent so we never
    // need a back-reference field on BookingItem nor a join that would duplicate rows under pagination. Matched by
    // the item-type cadastro code (stable identifier).
    private static Specification<BookingRequest> itemTypeFilter(String typeCode) {
        return (root, query, cb) -> {
            if (typeCode == null) {
                return cb.conjunction();
            }
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<BookingRequest> parent = sub.correlate(root);
            Join<BookingRequest, BookingItem> items = parent.join("items");
            sub.select(cb.literal(1)).where(cb.equal(items.join("type").get("code"), typeCode));
            return cb.exists(sub);
        };
    }

    // "Has at least one failed item": an EXISTS over the request's items with status FAILED.
    private static Specification<BookingRequest> failedItemsFilter(Boolean hasFailedItems) {
        return (root, query, cb) -> {
            if (hasFailedItems == null || !hasFailedItems) {
                return cb.conjunction();
            }
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<BookingRequest> parent = sub.correlate(root);
            Join<BookingRequest, BookingItem> items = parent.join("items");
            sub.select(cb.literal(1)).where(cb.equal(items.get("status"), BookingItemStatus.FAILED));
            return cb.exists(sub);
        };
    }
}
