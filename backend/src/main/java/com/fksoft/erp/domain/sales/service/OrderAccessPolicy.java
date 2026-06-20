package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.sales.model.CommercialOrder;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Business authorization for reading Commercial Orders (§10), mirroring the Proposal read model as three
 * escalating visibility tiers driven by scopes:
 *
 * <ul>
 *   <li><b>own only</b> ({@code sales:order:read}) — only Orders the user is responsible for;
 *   <li><b>own + unassigned pool</b> (also {@code sales:order:read:unassigned}) — plus Orders with no
 *       responsible;
 *   <li><b>all</b> ({@code sales:order:read:all}) — every Order (Managers, Board).
 * </ul>
 */
@Component
public class OrderAccessPolicy {

    /**
     * Builds the visibility predicate for the Commercial Order list for the given user.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return a Specification restricting visible Orders (always-true when {@code canSeeAll})
     */
    public Specification<CommercialOrder> visibleTo(UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var own = cb.equal(root.get("responsiblePersonId"), userId);
            return canSeeUnassigned ? cb.or(own, cb.isNull(root.get("responsiblePersonId"))) : own;
        };
    }

    /**
     * Tells whether a user may see a single Commercial Order: they hold read-all (sees all), are its
     * responsible, or it is unassigned and they may see the pool.
     *
     * @param order the commercial order
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return {@code true} if the order is visible to the user
     */
    public boolean canSee(CommercialOrder order, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return true;
        }
        if (order.responsiblePersonId() == null) {
            return canSeeUnassigned;
        }
        return order.responsiblePersonId().equals(userId);
    }
}
