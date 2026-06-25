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
     * All Receivables whose status is in the given set — used by the daily overdue check to load the operational
     * ({@code OPEN}/{@code PARTIALLY_PAID}) receivables that may have become overdue.
     *
     * @param statuses the statuses to match
     * @return the matching receivables
     */
    List<Receivable> findByStatusIn(Collection<ReceivableStatus> statuses);

    /**
     * The Receivables of the given Orders — used to batch-resolve the order → active-Receivable status for the
     * operational Commission list (avoiding an N+1 per row).
     *
     * @param commercialOrderIds the source order ids
     * @return the matching receivables
     */
    List<Receivable> findByCommercialOrderIdIn(Collection<UUID> commercialOrderIds);

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
