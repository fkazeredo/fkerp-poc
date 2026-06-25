package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.commission.model.CommissionTargetType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create or update a Commission Rule.
 *
 * @param name the rule name — required
 * @param percentage the commission percentage of the received amount — required, greater than zero, at most 100
 * @param targetType the commercial actor the rule targets — required
 * @param targetUserId the specific user the rule targets, or {@code null} for all actors of the type
 * @param startDate the validity start date — required
 * @param endDate the validity end date, or {@code null}
 * @param notes optional free-text notes
 * @param allowAboveLimit whether the caller explicitly allows a percentage above the configured safe limit (a
 *     nullable {@code Boolean} so the JSON field is optional — absent is treated as {@code false})
 */
public record CreateCommissionRuleRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull @Positive @DecimalMax("100") BigDecimal percentage,
        @NotNull CommissionTargetType targetType,
        UUID targetUserId,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 2000) String notes,
        Boolean allowAboveLimit) {}
