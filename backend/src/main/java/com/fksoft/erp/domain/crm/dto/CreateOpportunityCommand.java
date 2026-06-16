package com.fksoft.erp.domain.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input for creating an Opportunity from a Qualified Lead. Built by the delivery layer after boundary
 * validation.
 *
 * @param leadId the source (qualified) lead id (required)
 * @param responsiblePersonId responsible user id; when null, the lead's responsible is kept by default
 * @param productType estimated product type / interest area (free text) or null
 * @param estimatedValue optional estimated value ({@code >= 0}) or null
 * @param expectedCloseDate optional expected closing date or null
 * @param initialNote optional initial commercial note or null
 */
public record CreateOpportunityCommand(
        UUID leadId,
        UUID responsiblePersonId,
        String productType,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        String initialNote) {}
