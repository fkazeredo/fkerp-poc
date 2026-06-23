package com.fksoft.erp.domain.financial.service;

import com.fksoft.erp.domain.financial.model.Receivable;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read visibility for Receivables, two escalating tiers:
 *
 * <ul>
 *   <li>{@code financial:receivable:read} — own only (the financial responsible);
 *   <li>{@code financial:receivable:read:all} — all receivables.
 * </ul>
 *
 * Applied as a query Specification on the list and a {@code canSee} check on the single record, so filters and
 * detail can never bypass it. The backend is the only authority.
 */
@Component
public class ReceivableAccessPolicy {

    /**
     * The visibility predicate for the Receivable list.
     *
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @return a Specification restricting visible Receivables (always-true when {@code canSeeAll})
     */
    public Specification<Receivable> visibleTo(UUID userId, boolean canSeeAll) {
        if (canSeeAll) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("financialResponsiblePersonId"), userId);
    }

    /**
     * Whether the user may see a single Receivable.
     *
     * @param receivable the receivable
     * @param userId the current user id
     * @param canSeeAll whether the user holds the read-all scope
     * @return {@code true} if visible to the user
     */
    public boolean canSee(Receivable receivable, UUID userId, boolean canSeeAll) {
        return canSeeAll || userId.equals(receivable.financialResponsiblePersonId());
    }
}
