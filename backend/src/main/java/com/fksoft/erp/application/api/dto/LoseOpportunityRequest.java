package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.model.OpportunityLossReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to mark an Opportunity as lost.
 *
 * @param reason the loss reason (required; an unknown value is rejected as a 400)
 * @param note optional loss note
 */
public record LoseOpportunityRequest(
        @NotNull(message = "Motivo da perda é obrigatório") OpportunityLossReason reason,
        @Size(max = 2000, message = "Anotação muito longa") String note) {}
