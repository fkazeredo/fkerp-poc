package com.fksoft.erp.domain.crm.repository;

import java.time.Instant;
import java.util.UUID;

/** Read projection: the latest interaction date and type label for a lead. */
public interface LatestInteractionRow {

    UUID getLeadId();

    Instant getOccurredAt();

    String getTypeLabel();
}
