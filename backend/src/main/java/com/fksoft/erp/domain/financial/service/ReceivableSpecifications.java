package com.fksoft.erp.domain.financial.service;

import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.service.data.ReceivableSearchCriteria;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Receivable list. */
public final class ReceivableSpecifications {

    private ReceivableSpecifications() {}

    /**
     * Combines the filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<Receivable> matching(ReceivableSearchCriteria c) {
        return Specification.allOf(
                statusFilter(c.statuses()),
                orderFilter(c.commercialOrderId()),
                orderNumberFilter(c.orderNumber()),
                payerFilter(c.payer()),
                dueDateFilter(c.dueFrom(), c.dueTo()),
                createdFilter(c.createdFrom(), c.createdTo()),
                personFilter("commercialResponsiblePersonId", c.commercialResponsibleId()),
                personFilter("financialResponsiblePersonId", c.financialResponsibleId()),
                amountFilter(c.amountMin(), c.amountMax()),
                overdueOnlyFilter(c.overdueOnly()));
    }

    // The settled PAID and CANCELLED receivables never appear by default; pass them in the status filter to see them.
    private static Specification<Receivable> statusFilter(Set<String> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return root.get("status").in(ReceivableStatus.operational());
            }
            Set<ReceivableStatus> parsed = statuses.stream()
                    .map(ReceivableSpecifications::toStatus)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return parsed.isEmpty() ? cb.disjunction() : root.get("status").in(parsed);
        };
    }

    private static Specification<Receivable> orderFilter(UUID commercialOrderId) {
        return (root, query, cb) -> commercialOrderId == null
                ? cb.conjunction()
                : cb.equal(root.get("commercialOrderId"), commercialOrderId);
    }

    // Restrict to the source Commercial Order matching the given human number (PC-000n) via a subquery on orders.
    private static Specification<Receivable> orderNumberFilter(Long orderNumber) {
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

    // Match the payer by a case-insensitive substring of the Customer name via a subquery on customers.
    private static Specification<Receivable> payerFilter(String payer) {
        return (root, query, cb) -> {
            if (payer == null || payer.isBlank()) {
                return cb.conjunction();
            }
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<Customer> customer = sub.from(Customer.class);
            sub.select(customer.get("id"))
                    .where(cb.like(
                            cb.lower(customer.get("name")),
                            "%" + payer.toLowerCase().trim() + "%"));
            return root.get("customerId").in(sub);
        };
    }

    private static Specification<Receivable> dueDateFilter(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("dueDate"), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("dueDate"), to));
            }
            return predicate;
        };
    }

    private static Specification<Receivable> createdFilter(Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), to));
            }
            return predicate;
        };
    }

    private static Specification<Receivable> personFilter(String attribute, UUID personId) {
        return (root, query, cb) -> personId == null ? cb.conjunction() : cb.equal(root.get(attribute), personId);
    }

    private static Specification<Receivable> amountFilter(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (min != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("totalAmount"), min));
            }
            if (max != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("totalAmount"), max));
            }
            return predicate;
        };
    }

    // Overdue is the stored OVERDUE status (the daily overdue check flags past-due receivables with a balance,
    // per-installment-precise) — the single source of truth, so the filter matches it exactly.
    private static Specification<Receivable> overdueOnlyFilter(boolean overdueOnly) {
        return (root, query, cb) ->
                !overdueOnly ? cb.conjunction() : cb.equal(root.get("status"), ReceivableStatus.OVERDUE);
    }

    private static ReceivableStatus toStatus(String value) {
        try {
            return ReceivableStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
