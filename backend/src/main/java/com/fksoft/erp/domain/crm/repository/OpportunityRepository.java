package com.fksoft.erp.domain.crm.repository;

import com.fksoft.erp.domain.crm.model.Opportunity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for the {@link Opportunity} aggregate. */
public interface OpportunityRepository extends JpaRepository<Opportunity, UUID>, JpaSpecificationExecutor<Opportunity> {

    /**
     * The Opportunity created from the given Lead, if any (a Lead originates at most one).
     *
     * @param leadId the source lead id
     * @return the opportunity, or empty
     */
    Optional<Opportunity> findByLeadId(UUID leadId);

    /**
     * The most recent activity instant per Opportunity (batched, for the operational list — avoids N+1).
     *
     * @param opportunityIds the opportunity ids on the page
     * @return one row per Opportunity that has at least one activity
     */
    @Query(
            value =
                    """
                    SELECT a.opportunity_id AS opportunityId, MAX(a.occurred_at) AS lastActivityAt
                    FROM opportunity_activities a
                    WHERE a.opportunity_id IN (:opportunityIds)
                    GROUP BY a.opportunity_id
                    """,
            nativeQuery = true)
    List<OpportunityLastActivityRow> findLastActivityAt(@Param("opportunityIds") Collection<UUID> opportunityIds);
}
