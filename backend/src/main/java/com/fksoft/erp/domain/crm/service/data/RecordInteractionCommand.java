package com.fksoft.erp.domain.crm.service.data;

import java.time.Instant;
import java.util.UUID;

/**
 * Input to register a Lead interaction. Domain-side command so the Application Service stays free of
 * delivery DTOs.
 *
 * @param typeId the interaction type id (active)
 * @param resultId the interaction result id (active)
 * @param description the interaction description (required)
 * @param occurredAt when the interaction happened (past or present)
 * @param nextContactAt the scheduled next contact, or {@code null}
 */
public record RecordInteractionCommand(
        UUID typeId, UUID resultId, String description, Instant occurredAt, Instant nextContactAt) {}
