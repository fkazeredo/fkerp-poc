package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Minimal response for a created Opportunity (entity-free transport DTO). A new Opportunity always
 * starts at {@code NEW_OPPORTUNITY}.
 */
public record OpportunityResponse(UUID id, String stage) {}
