package com.fksoft.erp.domain.crm.service.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to register a commercial activity on an Opportunity (groups the activity fields so the service
 * method stays within the parameter limit).
 *
 * @param typeId the activity-type cadastro id
 * @param resultId the activity-result cadastro id
 * @param description what happened
 * @param occurredAt when the activity happened
 * @param nextActionDate optional planned next action date
 */
public record RecordActivityCommand(
        UUID typeId, UUID resultId, String description, Instant occurredAt, LocalDate nextActionDate) {}
