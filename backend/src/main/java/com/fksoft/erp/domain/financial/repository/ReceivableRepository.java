package com.fksoft.erp.domain.financial.repository;

import com.fksoft.erp.domain.financial.model.Receivable;
import com.fksoft.erp.domain.financial.model.ReceivableStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Repository for the {@link Receivable} aggregate (Financial Operations). */
public interface ReceivableRepository extends JpaRepository<Receivable, UUID>, JpaSpecificationExecutor<Receivable> {

    /**
     * The first Receivable of the given Order whose status is in the given set — used to enforce "at most one
     * active Receivable per Order" by passing the active statuses.
     *
     * @param commercialOrderId the source order id
     * @param statuses the statuses to match (the active ones, for the uniqueness check)
     * @return an existing matching receivable, or empty
     */
    Optional<Receivable> findFirstByCommercialOrderIdAndStatusIn(
            UUID commercialOrderId, Collection<ReceivableStatus> statuses);

    /**
     * The ids of the Commercial Orders that already have an active (non-cancelled) Receivable — used to
     * exclude them from the "orders eligible for a receivable" list.
     *
     * @return the order ids with an active receivable
     */
    @Query("SELECT r.commercialOrderId FROM Receivable r WHERE r.status <> "
            + "com.fksoft.erp.domain.financial.model.ReceivableStatus.CANCELLED")
    List<UUID> findOrderIdsWithActiveReceivable();
}
