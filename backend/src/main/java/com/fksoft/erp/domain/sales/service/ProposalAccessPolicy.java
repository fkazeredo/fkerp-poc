package com.fksoft.erp.domain.sales.service;

import com.fksoft.erp.domain.sales.model.Proposal;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Business authorization for reading Proposals (§10), mirroring the Opportunity read model as three
 * escalating visibility tiers driven by scopes:
 *
 * <ul>
 *   <li><b>own only</b> ({@code sales:proposal:read}) — only Proposals the user is responsible for;
 *   <li><b>own + unassigned pool</b> (also {@code sales:proposal:read:unassigned}) — plus Proposals with
 *       no responsible;
 *   <li><b>all</b> ({@code sales:proposal:read:all}) — every Proposal (Managers, Admin, Board).
 * </ul>
 *
 * Applied as a query Specification so restricted Proposals are never fetched — the list cannot expose them.
 */
@Component
public class ProposalAccessPolicy {

    /**
     * Builds the visibility predicate for the given user.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return a Specification restricting visible Proposals (always-true when {@code canSeeAll})
     */
    public Specification<Proposal> visibleTo(UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var own = cb.equal(root.get("responsiblePersonId"), userId);
            return canSeeUnassigned ? cb.or(own, cb.isNull(root.get("responsiblePersonId"))) : own;
        };
    }

    /**
     * Tells whether a user may see a single Proposal: they hold read-all (sees all), are its responsible,
     * or it is unassigned and they may see the pool.
     *
     * @param proposal the proposal
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @param canSeeUnassigned whether the user may also see the unassigned pool
     * @return {@code true} if the proposal is visible to the user
     */
    public boolean canSee(Proposal proposal, UUID userId, boolean canSeeAll, boolean canSeeUnassigned) {
        if (canSeeAll) {
            return true;
        }
        if (proposal.responsiblePersonId() == null) {
            return canSeeUnassigned;
        }
        return proposal.responsiblePersonId().equals(userId);
    }
}
