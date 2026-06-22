package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Minimal response for a created Proposal (entity-free transport DTO). A new Proposal always starts at
 * {@code DRAFT}.
 */
public record ProposalResponse(UUID id, String status) {}
