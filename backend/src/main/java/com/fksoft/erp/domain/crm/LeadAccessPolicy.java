package com.fksoft.erp.domain.crm;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Business authorization for reading Leads (§10). A regular commercial user may only see the Leads
 * they are responsible for plus unassigned ones; a manager (scope {@code crm:lead:read:all}) sees
 * every Lead. Applied as a query Specification so restricted Leads are never fetched — search and
 * filters cannot expose them.
 */
@Component
public class LeadAccessPolicy {

    /**
     * Builds the visibility predicate for the given user.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the manager read-all scope
     * @return a Specification restricting visible Leads (always-true when {@code canSeeAll})
     */
    public Specification<Lead> visibleTo(UUID userId, boolean canSeeAll) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) ->
                cb.or(cb.equal(root.get("responsiblePersonId"), userId), cb.isNull(root.get("responsiblePersonId")));
    }
}
