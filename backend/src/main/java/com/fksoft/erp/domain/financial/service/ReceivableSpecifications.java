package com.fksoft.erp.domain.financial.service;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import com.fksoft.erp.domain.financial.service.data.ReceivableSearchCriteria;
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
        return Specification.allOf(statusFilter(c.statuses()), orderFilter(c.commercialOrderId()));
    }

    // Cancelled Receivables never appear by default; pass CANCELLED in the status filter explicitly to see them.
    private static Specification<Receivable> statusFilter(Set<String> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return root.get("status").in(ReceivableStatus.active());
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

    private static ReceivableStatus toStatus(String value) {
        try {
            return ReceivableStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
