package com.fksoft.erp.domain.crm.service.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for the operational Opportunity list (all optional). Empty {@code stages} means the
 * default operational view, which excludes LOST; include LOST explicitly to see lost Opportunities.
 *
 * @param stages stages to include (empty/null ⇒ all non-lost)
 * @param responsibleId restrict to this responsible user
 * @param unassignedOnly restrict to Opportunities with no responsible (takes precedence over responsibleId)
 * @param originId restrict to this origin
 * @param createdFrom inclusive lower bound on the creation instant
 * @param createdTo exclusive upper bound on the creation instant
 * @param expectedCloseFrom inclusive lower bound on the expected closing date
 * @param expectedCloseTo inclusive upper bound on the expected closing date
 * @param estimatedValueMin inclusive lower bound on the estimated value
 * @param estimatedValueMax inclusive upper bound on the estimated value
 * @param query free-text search over the Opportunity (title/product/interest) and the source Lead
 *     (name and contacts)
 */
public record OpportunitySearchCriteria(
        Set<String> stages,
        UUID responsibleId,
        boolean unassignedOnly,
        UUID originId,
        Instant createdFrom,
        Instant createdTo,
        LocalDate expectedCloseFrom,
        LocalDate expectedCloseTo,
        BigDecimal estimatedValueMin,
        BigDecimal estimatedValueMax,
        String query) {}
