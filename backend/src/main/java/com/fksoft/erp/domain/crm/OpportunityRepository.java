package com.fksoft.erp.domain.crm;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the {@link Opportunity} aggregate. */
public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    /**
     * The Opportunity created from the given Lead, if any (a Lead originates at most one).
     *
     * @param leadId the source lead id
     * @return the opportunity, or empty
     */
    Optional<Opportunity> findByLeadId(UUID leadId);
}
