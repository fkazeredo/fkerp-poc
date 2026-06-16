package com.fksoft.erp.domain.crm;

import java.util.List;

/**
 * Minimum top-of-funnel indicators over the Leads visible to the caller, in a period. Counts include
 * every status (Lost included). {@code waitingFirstContact} is the subset of NEW Leads with no
 * interaction.
 */
public record LeadIndicatorsView(
        long total,
        long newLeads,
        long contacted,
        long qualified,
        long lost,
        long waitingFirstContact,
        List<OriginCountView> byOrigin,
        List<ResponsibleCountView> byResponsible) {}
