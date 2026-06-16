package com.fksoft.erp.domain.crm;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Business authorization for reading Leads (§10), as three escalating visibility tiers driven by
 * scopes:
 *
 * <ul>
 *   <li><b>own only</b> ({@code crm:lead:read}) — only Leads the user is responsible for (external
 *       Sales Representatives);
 *   <li><b>own + unassigned pool</b> (also {@code crm:lead:read:unassigned}) — plus unassigned Leads
 *       the user may pick up (Sellers, Call Center);
 *   <li><b>all</b> ({@code crm:lead:read:all}) — every Lead (Managers, Admin, Board, Marketing).
 * </ul>
 *
 * Applied as a query Specification so restricted Leads are never fetched — search and filters cannot
 * expose them.
 */
@Component
public class LeadAccessPolicy {

    /**
     * Builds the visibility predicate for the given user.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the manager read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return a Specification restricting visible Leads (always-true when {@code canSeeAll})
     */
    public Specification<Lead> visibleTo(UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var own = cb.equal(root.get("responsiblePersonId"), userId);
            return canSeeUnassigned ? cb.or(own, cb.isNull(root.get("responsiblePersonId"))) : own;
        };
    }

    /**
     * Tells whether a user may see (and act on) a single Lead: they are a manager (sees all), its
     * responsible, or it is unassigned and they may see the pool.
     *
     * @param lead the lead
     * @param userId the current user id
     * @param canSeeAll whether the user holds the manager read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return {@code true} if the lead is visible to the user
     */
    public boolean canSee(Lead lead, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return true;
        }
        if (lead.responsiblePersonId() == null) {
            return canSeeUnassigned;
        }
        return lead.responsiblePersonId().equals(userId);
    }
}
