package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.service.data.OpportunitySearchCriteria;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic query predicates for the operational Opportunity list. */
public final class OpportunitySpecifications {

    private OpportunitySpecifications() {}

    /**
     * Combines all filter predicates (AND) for the given criteria.
     *
     * @param c the criteria
     * @return the combined Specification
     */
    public static Specification<Opportunity> matching(OpportunitySearchCriteria c) {
        return Specification.allOf(
                stageFilter(c.stages()),
                responsibleFilter(c.responsibleId(), c.unassignedOnly()),
                originFilter(c.originId()),
                createdRange(c.createdFrom(), c.createdTo()),
                expectedCloseRange(c.expectedCloseFrom(), c.expectedCloseTo()),
                estimatedValueRange(c.estimatedValueMin(), c.estimatedValueMax()),
                search(c.query()));
    }

    // Closed (won/lost) Opportunities never appear in the default operational list; include them in the
    // stage filter to see them.
    private static Specification<Opportunity> stageFilter(Set<OpportunityStage> stages) {
        return (root, query, cb) -> {
            if (stages == null || stages.isEmpty()) {
                return cb.not(root.get("stage").in(OpportunityStage.terminalStages()));
            }
            return root.get("stage").in(stages);
        };
    }

    private static Specification<Opportunity> responsibleFilter(UUID responsibleId, boolean unassignedOnly) {
        return (root, query, cb) -> {
            if (unassignedOnly) {
                return cb.isNull(root.get("responsiblePersonId"));
            }
            return responsibleId == null ? cb.conjunction() : cb.equal(root.get("responsiblePersonId"), responsibleId);
        };
    }

    private static Specification<Opportunity> originFilter(UUID originId) {
        return (root, query, cb) -> originId == null
                ? cb.conjunction()
                : cb.equal(root.get("origin").get("id"), originId);
    }

    private static Specification<Opportunity> createdRange(Instant from, Instant to) {
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

    private static Specification<Opportunity> expectedCloseRange(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expectedCloseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expectedCloseDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<Opportunity> estimatedValueRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (min != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("estimatedValue"), min));
            }
            if (max != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("estimatedValue"), max));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Free-text search over the Opportunity's own fields (title/product/main interest) and the source
     * Lead's name and contacts. The Opportunity keeps only {@code leadId} (no mapped relationship), so
     * the Lead match is a correlated {@code EXISTS} subquery on {@code lead.id = opportunity.leadId}.
     *
     * @param term the search term
     * @return the search Specification
     */
    private static Specification<Opportunity> search(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + term.trim().toLowerCase() + "%";
            Predicate ownFields = cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("productType")), like),
                    cb.like(cb.lower(root.get("mainInterest")), like));
            Subquery<UUID> lead = query.subquery(UUID.class);
            Root<Lead> l = lead.from(Lead.class);
            lead.select(l.get("id"))
                    .where(cb.and(
                            cb.equal(l.get("id"), root.get("leadId")),
                            cb.or(
                                    cb.like(cb.lower(l.get("name")), like),
                                    cb.like(cb.lower(l.get("phone")), like),
                                    cb.like(cb.lower(l.get("whatsapp")), like),
                                    cb.like(cb.lower(l.get("email")), like))));
            return cb.or(ownFields, cb.exists(lead));
        };
    }
}
