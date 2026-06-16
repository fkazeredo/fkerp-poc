package com.fksoft.erp.application.api.dto;

/**
 * Application version (entity-free transport DTO). The {@code version} follows SemVer
 * {@code MAJOR.MINOR.PATCH}.
 */
public record VersionResponse(String version) {}
