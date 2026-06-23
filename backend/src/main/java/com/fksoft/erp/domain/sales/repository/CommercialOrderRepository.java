package com.fksoft.erp.domain.sales.repository;

import com.fksoft.erp.domain.sales.model.CommercialOrder;
import com.fksoft.erp.domain.sales.model.CommercialOrderStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Repository for Commercial Orders (Sales &amp; Proposals). */
public interface CommercialOrderRepository
        extends JpaRepository<CommercialOrder, UUID>, JpaSpecificationExecutor<CommercialOrder> {

    /**
     * The first Order of the given Proposal whose status is one of the given set — used to enforce "at most
     * one active Order per Proposal" by passing the active statuses.
     *
     * @param proposalId the source proposal id
     * @param statuses the statuses to match (the active ones, for the uniqueness check)
     * @return an existing matching order, or empty
     */
    Optional<CommercialOrder> findFirstByProposalIdAndStatusIn(
            UUID proposalId, Collection<CommercialOrderStatus> statuses);

    /**
     * Draws the next human-friendly sequential order number from the order-number sequence.
     *
     * @return the next order number
     */
    @Query(value = "SELECT nextval('commercial_order_number_seq')", nativeQuery = true)
    long nextOrderNumber();
}
