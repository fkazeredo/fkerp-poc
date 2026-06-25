package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Trivial create response for a Commission Rule (a new rule is always active).
 *
 * @param id the new rule id
 * @param active whether the rule is active (always {@code true} on creation)
 */
public record CommissionRuleResponse(UUID id, boolean active) {}
