package com.fksoft.erp.domain.crm.service.data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input to edit an Opportunity's commercial details. Each field is optional; {@code null} clears it. The
 * main interest is not part of this edit (it stays as set at the Lead qualification).
 *
 * @param estimatedValue the estimated value ({@code >= 0}), or {@code null}
 * @param expectedCloseDate the expected closing date, or {@code null}
 * @param productType the product / service interest area, or {@code null}
 * @param notes the commercial notes, or {@code null}
 */
public record UpdateOpportunityDetailsCommand(
        BigDecimal estimatedValue, LocalDate expectedCloseDate, String productType, String notes) {}
