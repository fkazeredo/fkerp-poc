package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.sales.model.BookingNeed;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.service.data.CommercialOrderSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Commercial Order list. */
public final class CommercialOrderSpecifications {

    // The active (non-cancelled) statuses, shown by default.
    private static final Set<CommercialOrderStatus> ACTIVE_STATUSES = CommercialOrderStatus.active();

    private CommercialOrderSpecifications() {}

    /**
     * Combines all filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<CommercialOrder> matching(CommercialOrderSearchCriteria c) {
        return Specification.allOf(
                statusFilter(c.statuses()),
                responsibleFilter(c.responsibleId(), c.unassignedOnly()),
                createdRange(c.createdFrom(), c.createdTo()),
                totalRange(c.totalMin(), c.totalMax()),
                bookingNeedFilter(c.bookingNeed()),
                search(c.query()));
    }

    // Cancelled Orders never appear by default; pass CANCELLED in the status filter explicitly to see them.
    private static Specification<CommercialOrder> statusFilter(Set<String> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return root.get("status").in(ACTIVE_STATUSES);
            }
            Set<CommercialOrderStatus> parsed = statuses.stream()
                    .map(CommercialOrderSpecifications::toStatus)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return parsed.isEmpty() ? cb.disjunction() : root.get("status").in(parsed);
        };
    }

    private static CommercialOrderStatus toStatus(String value) {
        try {
            return CommercialOrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Specification<CommercialOrder> responsibleFilter(UUID responsibleId, boolean unassignedOnly) {
        return (root, query, cb) -> {
            if (unassignedOnly) {
                return cb.isNull(root.get("responsiblePersonId"));
            }
            return responsibleId == null ? cb.conjunction() : cb.equal(root.get("responsiblePersonId"), responsibleId);
        };
    }

    private static Specification<CommercialOrder> createdRange(Instant from, Instant to) {
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

    private static Specification<CommercialOrder> totalRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (min != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("total"), min));
            }
            if (max != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("total"), max));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // The booking-need filter maps to the Order status (PENDING_BOOKING / BOOKING_NOT_REQUIRED).
    private static Specification<CommercialOrder> bookingNeedFilter(BookingNeed bookingNeed) {
        return (root, query, cb) ->
                bookingNeed == null ? cb.conjunction() : cb.equal(root.get("status"), bookingNeed.toStatus());
    }

    /**
     * Free-text search over the source Proposal's title. The Order keeps only {@code proposalId} (no mapped
     * relationship), so the match is a correlated {@code EXISTS} subquery on
     * {@code proposal.id = order.proposalId}.
     *
     * @param term the search term
     * @return the search Specification
     */
    private static Specification<CommercialOrder> search(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + term.trim().toLowerCase() + "%";
            Subquery<UUID> proposal = query.subquery(UUID.class);
            Root<Proposal> p = proposal.from(Proposal.class);
            proposal.select(p.get("id"))
                    .where(cb.and(
                            cb.equal(p.get("id"), root.get("proposalId")), cb.like(cb.lower(p.get("title")), like)));
            return cb.exists(proposal);
        };
    }
}
