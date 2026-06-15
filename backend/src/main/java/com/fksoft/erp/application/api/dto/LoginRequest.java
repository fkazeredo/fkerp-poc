package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials.
 *
 * @param username the username
 * @param password the raw password
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
