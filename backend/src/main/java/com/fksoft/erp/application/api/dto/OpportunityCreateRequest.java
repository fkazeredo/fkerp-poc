package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create an Opportunity from a Qualified Lead. The main interest, origin and (by default)
 * the responsible are taken from the source Lead; the fields here are the optional commercial estimates.
 *
 * @param leadId the source (qualified) lead id (required)
 * @param responsiblePersonId optional responsible override (defaults to the lead's responsible)
 * @param productType optional estimated product type / interest area (free text)
 * @param estimatedValue optional estimated value ({@code >= 0})
 * @param expectedCloseDate optional expected closing date
 * @param initialNote optional initial commercial note
 */
public record OpportunityCreateRequest(
        @NotNull UUID leadId,
        UUID responsiblePersonId,
        @Size(max = 200) String productType,
        @PositiveOrZero BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        @Size(max = 2000) String initialNote) {}
