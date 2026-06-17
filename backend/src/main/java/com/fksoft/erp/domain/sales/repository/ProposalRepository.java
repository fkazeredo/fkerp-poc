package com.fksoft.erp.domain.sales.repository;

import com.fksoft.erp.domain.sales.model.Proposal;
import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository for the {@link Proposal} aggregate. */
public interface ProposalRepository extends JpaRepository<Proposal, UUID>, JpaSpecificationExecutor<Proposal> {

    /**
     * The first Proposal of the given Opportunity whose status is one of the given set — used to enforce
     * "at most one open Proposal per Opportunity" by passing the open statuses.
     *
     * @param opportunityId the source opportunity id
     * @param statuses the statuses to match (the open ones, for the uniqueness check)
     * @return an existing matching proposal, or empty
     */
    Optional<Proposal> findFirstByOpportunityIdAndStatusIn(UUID opportunityId, Collection<ProposalStatus> statuses);
}
