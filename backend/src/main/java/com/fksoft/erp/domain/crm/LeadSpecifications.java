package com.fksoft.erp.domain.crm;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Lead list. */
public final class LeadSpecifications {

    private LeadSpecifications() {}

    /**
     * Combines all filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<Lead> matching(LeadSearchCriteria c) {
        return Specification.allOf(
                fetchOrigin(),
                statusFilter(c.statuses()),
                originFilter(c.originId()),
                responsibleFilter(c.responsibleId(), c.unassignedOnly()),
                createdRange(c.createdFrom(), c.createdTo()),
                search(c.query()));
    }

    // Fetch-join origin on the data query (not the count query) to avoid N+1 on its label.
    private static Specification<Lead> fetchOrigin() {
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != null && !Long.class.equals(resultType) && !long.class.equals(resultType)) {
                root.fetch("origin", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    private static Specification<Lead> statusFilter(Set<LeadStatus> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return cb.notEqual(root.get("status"), LeadStatus.LOST);
            }
            return root.get("status").in(statuses);
        };
    }

    private static Specification<Lead> originFilter(UUID originId) {
        return (root, query, cb) -> originId == null
                ? cb.conjunction()
                : cb.equal(root.get("origin").get("id"), originId);
    }

    private static Specification<Lead> responsibleFilter(UUID responsibleId, boolean unassignedOnly) {
        return (root, query, cb) -> {
            if (unassignedOnly) {
                return cb.isNull(root.get("responsiblePersonId"));
            }
            return responsibleId == null ? cb.conjunction() : cb.equal(root.get("responsiblePersonId"), responsibleId);
        };
    }

    private static Specification<Lead> createdRange(Instant from, Instant to) {
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

    private static Specification<Lead> search(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + term.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("phone")), like),
                    cb.like(cb.lower(root.get("whatsapp")), like),
                    cb.like(cb.lower(root.get("email")), like));
        };
    }
}
