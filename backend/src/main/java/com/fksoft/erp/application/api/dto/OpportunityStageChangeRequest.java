package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.model.OpportunityStage;
import jakarta.validation.constraints.NotNull;

/**
 * Request to move an Opportunity to another pipeline stage.
 *
 * @param stage the destination stage (required; an unknown value is rejected as a 400)
 */
public record OpportunityStageChangeRequest(@NotNull OpportunityStage stage) {}
