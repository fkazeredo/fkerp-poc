package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.ProposalStatus;
import java.util.UUID;

/**
 * Minimal response for a created Proposal (entity-free transport DTO). A new Proposal always starts at
 * {@link ProposalStatus#DRAFT}.
 */
public record ProposalResponse(UUID id, ProposalStatus status) {}
