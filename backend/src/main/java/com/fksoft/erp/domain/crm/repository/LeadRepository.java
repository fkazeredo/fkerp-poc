package com.fksoft.erp.domain.crm.repository;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadInteraction;
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

    /**
     * Open (non-lost) Leads that duplicate the given contact data, oldest first. A phone or WhatsApp
     * number matches either contact field (the same number is often reused across both); the e-mail
     * is matched case-insensitively (pass it already lower-cased). Lost Leads never match — they may
     * be recontacted. Used to block duplicates at registration (the DB partial unique indexes are the
     * last-resort guard).
     *
     * @param phone the new lead's phone (digits, or {@code null})
     * @param whatsapp the new lead's WhatsApp (digits, or {@code null})
     * @param email the new lead's e-mail, lower-cased (or {@code null})
     * @return matching open Leads, oldest first (empty when none)
     */
    @Query(
            """
            SELECT l FROM Lead l
            WHERE l.status <> com.fksoft.erp.domain.crm.model.LeadStatus.LOST
              AND ( (:email IS NOT NULL AND LOWER(l.email) = :email)
                 OR (:phone IS NOT NULL AND (l.phone = :phone OR l.whatsapp = :phone))
                 OR (:whatsapp IS NOT NULL AND (l.phone = :whatsapp OR l.whatsapp = :whatsapp)) )
            ORDER BY l.createdAt ASC
            """)
    List<Lead> findOpenDuplicates(
            @Param("phone") String phone, @Param("whatsapp") String whatsapp, @Param("email") String email);
}
