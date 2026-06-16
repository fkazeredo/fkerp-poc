package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.crm.service.data.OpportunitySearchCriteria;
import java.util.Set;
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
        return Specification.allOf(stageFilter(c.stages()), search(c.query()));
    }

    // Lost Opportunities never appear in the default operational list; include LOST in the filter to see them.
    private static Specification<Opportunity> stageFilter(Set<OpportunityStage> stages) {
        return (root, query, cb) -> {
            if (stages == null || stages.isEmpty()) {
                return cb.notEqual(root.get("stage"), OpportunityStage.LOST);
            }
            return root.get("stage").in(stages);
        };
    }

    private static Specification<Opportunity> search(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + term.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("productType")), like),
                    cb.like(cb.lower(root.get("mainInterest")), like));
        };
    }
}
