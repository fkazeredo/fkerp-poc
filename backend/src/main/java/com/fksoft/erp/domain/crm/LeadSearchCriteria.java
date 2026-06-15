package com.fksoft.erp.domain.crm;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for the operational Lead list (all optional). Empty {@code statuses} means the
 * default operational view, which excludes LOST; include LOST explicitly to see lost leads.
 *
 * @param statuses statuses to include (empty/null ⇒ all non-lost)
 * @param originId restrict to this origin
 * @param responsibleId restrict to this responsible user
 * @param unassignedOnly restrict to leads with no responsible (takes precedence over responsibleId)
 * @param createdFrom inclusive lower bound on creation instant
 * @param createdTo exclusive upper bound on creation instant
 * @param query free-text search over name and contacts
 */
public record LeadSearchCriteria(
        Set<LeadStatus> statuses,
        UUID originId,
        UUID responsibleId,
        boolean unassignedOnly,
        Instant createdFrom,
        Instant createdTo,
        String query) {}
