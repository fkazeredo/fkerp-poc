package com.fksoft.erp.infra.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application configuration, validated at startup so the app fails fast when a required
 * property is missing or blank.
 *
 * @param name human-readable system name
 * @param version running application version (SemVer {@code MAJOR.MINOR.PATCH}), from {@code APP_VERSION}
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String name, @NotBlank String version) {}
