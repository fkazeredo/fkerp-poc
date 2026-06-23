package com.fksoft.erp.domain.crm.service;

import com.fksoft.erp.domain.crm.model.Lead;
import com.fksoft.erp.domain.crm.model.LeadInteraction;
import com.fksoft.erp.domain.crm.model.LeadStatus;
import com.fksoft.erp.domain.crm.model.PendingLeadReasons;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicate selecting Leads that need action — the OR of the fixed pre-defined pending conditions.
 * Mirrors {@link PendingLeadReasons#of} so the page contains exactly the Leads that have at least one reason.
 */
public final class LeadPendingSpecifications {

    private LeadPendingSpecifications() {}

    /**
     * A Lead is pending when at least one of the pre-defined reasons applies.
     *
     * @param now the reference instant for "overdue"
     * @return the pending Specification
     */
    public static Specification<Lead> pending(Instant now) {
        return (root, query, cb) -> {
            var status = root.get("status");

            Predicate unassigned =
                    cb.and(cb.isNull(root.get("responsiblePersonId")), cb.notEqual(status, LeadStatus.LOST));

            Subquery<UUID> interactions = query.subquery(UUID.class);
            var li = interactions.from(LeadInteraction.class);
            interactions.select(li.get("leadId")).where(cb.equal(li.get("leadId"), root.get("id")));
            Predicate newWithoutInteraction = cb.and(cb.equal(status, LeadStatus.NEW), cb.not(cb.exists(interactions)));

            Predicate overdueNextContact = cb.and(
                    cb.isNotNull(root.get("nextContactAt")),
                    cb.lessThan(root.<Instant>get("nextContactAt"), now),
                    cb.notEqual(status, LeadStatus.QUALIFIED),
                    cb.notEqual(status, LeadStatus.LOST));

            Predicate contactedWithoutOutcome =
                    cb.and(cb.equal(status, LeadStatus.CONTACTED), cb.isNull(root.get("nextContactAt")));

            return cb.or(unassigned, newWithoutInteraction, overdueNextContact, contactedWithoutOutcome);
        };
    }
}
