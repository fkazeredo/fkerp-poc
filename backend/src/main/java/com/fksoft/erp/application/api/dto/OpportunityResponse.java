package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.OpportunityStage;
import java.util.UUID;

/**
 * Minimal response for a created Opportunity (entity-free transport DTO). A new Opportunity always
 * starts at {@link OpportunityStage#NEW_OPPORTUNITY}.
 */
public record OpportunityResponse(UUID id, OpportunityStage stage) {}
