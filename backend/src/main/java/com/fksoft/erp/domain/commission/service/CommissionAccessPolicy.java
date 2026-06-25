package com.fksoft.erp.domain.commission.service;

import com.fksoft.erp.domain.commission.model.Commission;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read visibility for Commissions, two escalating tiers:
 *
 * <ul>
 *   <li>{@code commission:read} — own only (the beneficiary);
 *   <li>{@code commission:read:all} — all commissions.
 * </ul>
 *
 * Applied as a query Specification on the list and a {@code canSee} check on the single record, so filters and the
 * detail can never bypass it. Sellers/representatives hold the own tier (their own commissions only); managers, the
 * Board and Finance hold read-all. The backend is the only authority.
 */
@Component
public class CommissionAccessPolicy {

    /**
     * The visibility predicate for the Commission list.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @return a Specification restricting visible Commissions (always-true when {@code canSeeAll})
     */
    public Specification<Commission> visibleTo(UUID userId, boolean canSeeAll) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("beneficiaryUserId"), userId);
    }

    /**
     * Whether the user may see a single Commission.
     *
     * @param commission the commission
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @return {@code true} if visible to the user
     */
    public boolean canSee(Commission commission, UUID userId, boolean canSeeAll) {
        return canSeeAll || userId.equals(commission.beneficiaryUserId());
    }
}
