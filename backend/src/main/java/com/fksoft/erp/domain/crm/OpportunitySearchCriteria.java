package com.fksoft.erp.domain.crm;

import java.util.Set;

/**
 * Filter criteria for the operational Opportunity list (all optional). Empty {@code stages} means the
 * default operational view, which excludes LOST; include LOST explicitly to see lost Opportunities.
 *
 * @param stages stages to include (empty/null ⇒ all non-lost)
 * @param query free-text search over name, product type and main interest
 */
public record OpportunitySearchCriteria(Set<OpportunityStage> stages, String query) {}
