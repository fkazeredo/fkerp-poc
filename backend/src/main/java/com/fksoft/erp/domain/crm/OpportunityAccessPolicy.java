package com.fksoft.erp.domain.crm;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Business authorization for reading Opportunities (§10), mirroring the Lead read model as three
 * escalating visibility tiers driven by scopes:
 *
 * <ul>
 *   <li><b>own only</b> ({@code crm:opportunity:read}) — only Opportunities the user is responsible
 *       for (external Sales Representatives);
 *   <li><b>own + unassigned pool</b> (also {@code crm:opportunity:read:unassigned}) — plus
 *       Opportunities with no responsible (Sellers, Call Center);
 *   <li><b>all</b> ({@code crm:opportunity:read:all}) — every Opportunity (Managers, Admin, Board,
 *       Marketing).
 * </ul>
 *
 * Applied as a query Specification so restricted Opportunities are never fetched — filters and search
 * cannot expose them.
 */
@Component
public class OpportunityAccessPolicy {

    /**
     * Builds the visibility predicate for the given user.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return a Specification restricting visible Opportunities (always-true when {@code canSeeAll})
     */
    public Specification<Opportunity> visibleTo(UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var own = cb.equal(root.get("responsiblePersonId"), userId);
            return canSeeUnassigned ? cb.or(own, cb.isNull(root.get("responsiblePersonId"))) : own;
        };
    }
}
