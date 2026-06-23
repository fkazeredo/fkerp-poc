package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import com.fksoft.erp.domain.sales.service.data.ProposalSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Proposal list. */
public final class ProposalSpecifications {

    private ProposalSpecifications() {}

    /**
     * Combines all filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<Proposal> matching(ProposalSearchCriteria c) {
        return Specification.allOf(
                statusFilter(c.statuses()),
                responsibleFilter(c.responsibleId(), c.unassignedOnly()),
                opportunityFilter(c.opportunityId()),
                createdRange(c.createdFrom(), c.createdTo()),
                validRange(c.validFrom(), c.validTo()),
                totalRange(c.totalMin(), c.totalMax()),
                search(c.query()));
    }

    // Terminal-negative Proposals (REJECTED/EXPIRED/CANCELLED) never appear by default; pass them in the
    // status filter explicitly to see inactive Proposals.
    private static Specification<Proposal> statusFilter(Set<String> statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.isEmpty()) {
                return root.get("status").in(ProposalStatus.open());
            }
            Set<ProposalStatus> parsed = statuses.stream()
                    .map(ProposalSpecifications::toStatus)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return parsed.isEmpty() ? cb.disjunction() : root.get("status").in(parsed);
        };
    }

    private static ProposalStatus toStatus(String value) {
        try {
            return ProposalStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Specification<Proposal> responsibleFilter(UUID responsibleId, boolean unassignedOnly) {
        return (root, query, cb) -> {
            if (unassignedOnly) {
                return cb.isNull(root.get("responsiblePersonId"));
            }
            return responsibleId == null ? cb.conjunction() : cb.equal(root.get("responsiblePersonId"), responsibleId);
        };
    }

    private static Specification<Proposal> opportunityFilter(UUID opportunityId) {
        return (root, query, cb) ->
                opportunityId == null ? cb.conjunction() : cb.equal(root.get("opportunityId"), opportunityId);
    }

    private static Specification<Proposal> createdRange(Instant from, Instant to) {
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

    private static Specification<Proposal> validRange(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("validUntil"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("validUntil"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<Proposal> totalRange(BigDecimal min, BigDecimal max) {
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

    /**
     * Free-text search over the Proposal title and the source Opportunity's name. The Proposal keeps only
     * {@code opportunityId} (no mapped relationship), so the Opportunity match is a correlated {@code EXISTS}
     * subquery on {@code opportunity.id = proposal.opportunityId}.
     *
     * @param term the search term
     * @return the search Specification
     */
    private static Specification<Proposal> search(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + term.trim().toLowerCase() + "%";
            Subquery<UUID> opportunity = query.subquery(UUID.class);
            Root<Opportunity> o = opportunity.from(Opportunity.class);
            opportunity
                    .select(o.get("id"))
                    .where(cb.and(
                            cb.equal(o.get("id"), root.get("opportunityId")), cb.like(cb.lower(o.get("name")), like)));
            return cb.or(cb.like(cb.lower(root.get("title")), like), cb.exists(opportunity));
        };
    }
}
