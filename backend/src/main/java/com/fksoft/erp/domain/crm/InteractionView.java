package com.fksoft.erp.domain.crm;

import java.time.Instant;
import java.util.UUID;

/** Read view of a single interaction in a Lead's history. */
public record InteractionView(
        UUID id, String typeLabel, String resultLabel, String content, Instant occurredAt, String registeredByName) {}
