package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to edit an Opportunity's commercial details. All fields are optional; {@code null} clears the
 * field. The main interest is not editable here (it stays as set at the Lead qualification).
 *
 * @param estimatedValue the estimated value ({@code >= 0}), or {@code null}
 * @param expectedCloseDate the expected closing date, or {@code null}
 * @param productType the product / service interest area, or {@code null}
 * @param notes the commercial notes, or {@code null}
 */
public record UpdateOpportunityDetailsRequest(
        @PositiveOrZero BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        @Size(max = 200) String productType,
        @Size(max = 2000) String notes) {}
