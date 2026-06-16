package com.fksoft.erp.domain.crm.repository;

import java.time.Instant;
import java.util.UUID;

/** Projection of the most recent activity instant per Opportunity, for the operational list. */
public interface OpportunityLastActivityRow {

    UUID getOpportunityId();

    Instant getLastActivityAt();
}
