package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.OpportunityActivityResult;
import com.fksoft.erp.domain.crm.model.OpportunityActivityType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Input to register a commercial activity on an Opportunity (groups the activity fields so the service
 * method stays within the parameter limit).
 *
 * @param type the activity type
 * @param result the activity outcome
 * @param description what happened
 * @param occurredAt when the activity happened
 * @param nextActionDate optional planned next action date
 */
public record RecordActivityCommand(
        OpportunityActivityType type,
        OpportunityActivityResult result,
        String description,
        Instant occurredAt,
        LocalDate nextActionDate) {}
