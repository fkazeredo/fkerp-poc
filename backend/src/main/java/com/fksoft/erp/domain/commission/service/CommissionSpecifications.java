package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import com.fksoft.erp.domain.commission.service.data.CommissionSearchCriteria;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Commission list. */
public final class CommissionSpecifications {

    private CommissionSpecifications() {}

    /**
     * Combines the filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<Commission> matching(CommissionSearchCriteria c) {
        return Specification.allOf(
                statusFilter(c.statuses()),
                idFilter("beneficiaryUserId", c.beneficiaryUserId()),
                idFilter("commercialOrderId", c.commercialOrderId()),
                orderNumberFilter(c.orderNumber()),
                idFilter("ruleId", c.ruleId()),
                instantFilter("createdAt", c.createdFrom(), c.createdTo()),
                instantFilter("eligibleAt", c.eligibleFrom(), c.eligibleTo()),
                instantFilter("paidAt", c.paidFrom(), c.paidTo()),
                amountFilter(c.amountMin(), c.amountMax()));
    }

    // The settled PAID and the terminal REJECTED/CANCELLED never appear by default; pass them in the status filter.
    private static Specification<Commission> statusFilter(Set<String> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return root.get("status").in(CommissionStatus.operational());
            }
            Set<CommissionStatus> parsed = statuses.stream()
                    .map(CommissionSpecifications::toStatus)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return parsed.isEmpty() ? cb.disjunction() : root.get("status").in(parsed);
        };
    }

    private static Specification<Commission> idFilter(String attribute, UUID value) {
        return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(attribute), value);
    }

    // Restrict to the source Commercial Order matching the given human number (PC-000n) via a subquery on orders.
    private static Specification<Commission> orderNumberFilter(Long orderNumber) {
        return (root, query, cb) -> {
            if (orderNumber == null) {
                return cb.conjunction();
            }
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<CommercialOrder> order = sub.from(CommercialOrder.class);
            sub.select(order.get("id")).where(cb.equal(order.get("number"), orderNumber));
            return root.get("commercialOrderId").in(sub);
        };
    }

    private static Specification<Commission> instantFilter(String attribute, Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get(attribute), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get(attribute), to));
            }
            return predicate;
        };
    }

    private static Specification<Commission> amountFilter(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (min != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("amount"), min));
            }
            if (max != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("amount"), max));
            }
            return predicate;
        };
    }

    private static CommissionStatus toStatus(String value) {
        try {
            return CommissionStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
