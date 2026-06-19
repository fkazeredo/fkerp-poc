package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.ProposalRejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body to reject a Proposal under review. The reason is required (a fixed commercial set); the note
 * is an optional free-text detail.
 *
 * @param reason the rejection reason (required)
 * @param note an optional free-text note
 */
public record RejectProposalRequest(@NotNull ProposalRejectionReason reason, @Size(max = 2000) String note) {}
