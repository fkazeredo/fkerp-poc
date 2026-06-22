package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to move an Opportunity to another pipeline stage.
 *
 * @param stage the destination stage code (required; an unknown/disallowed value is rejected)
 */
public record OpportunityStageChangeRequest(@NotBlank String stage) {}
