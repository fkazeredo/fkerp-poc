package com.fksoft.erp.infra.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed Commission Management configuration, validated at startup (fails fast on a missing/invalid value).
 *
 * @param safeMaxPercentage the safe business limit for a Commission Rule percentage; a percentage above it is
 *     rejected unless the create/update explicitly allows exceeding it ({@code allowAboveLimit}). At most 100.
 */
@Validated
@ConfigurationProperties(prefix = "app.commission")
public record CommissionProperties(@NotNull @Positive @DecimalMax("100") BigDecimal safeMaxPercentage) {}
