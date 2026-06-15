package com.fksoft.erp.domain.crm;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for the {@link Lead} aggregate, with dynamic filtering for the operational list. */
public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    /**
     * Latest interaction (date + type label) per lead, for a set of leads. Native because
     * {@link LeadInteraction} has no back-reference to its lead and Postgres {@code DISTINCT ON}
     * resolves the latest row per lead in a single pass (avoids N+1).
     *
     * @param leadIds the lead ids to resolve (must be non-empty)
     * @return one row per lead that has at least one interaction
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (li.lead_id)
                           li.lead_id     AS leadId,
                           li.occurred_at AS occurredAt,
                           it.label       AS typeLabel
                    FROM lead_interactions li
                    JOIN interaction_types it ON it.id = li.type_id
                    WHERE li.lead_id IN (:leadIds)
                    ORDER BY li.lead_id, li.occurred_at DESC
                    """,
            nativeQuery = true)
    List<LatestInteractionRow> findLatestInteractions(@Param("leadIds") Collection<UUID> leadIds);
}
