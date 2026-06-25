package com.fksoft.erp.domain.commission.repository;

import com.fksoft.erp.domain.commission.model.Commission;
import com.fksoft.erp.domain.commission.model.CommissionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for the {@link Commission} aggregate (Commission Management). */
public interface CommissionRepository extends JpaRepository<Commission, UUID>, JpaSpecificationExecutor<Commission> {

    /**
     * The first Commission of the given Order whose status is in the given set — used to enforce "at most one active
     * Commission per Order" by passing the active statuses.
     *
     * @param commercialOrderId the source order id
     * @param statuses the statuses to match (the active ones, for the uniqueness check)
     * @return an existing matching commission, or empty
     */
    Optional<Commission> findFirstByCommercialOrderIdAndStatusIn(
            UUID commercialOrderId, Collection<CommissionStatus> statuses);
}
