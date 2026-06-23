package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body to reject a Proposal under review. The reason cadastro id is required; the note is an optional
 * free-text detail.
 *
 * @param reasonId the rejection-reason cadastro id (required; an unknown/inactive value is rejected as 422)
 * @param note an optional free-text note
 */
public record RejectProposalRequest(@NotNull UUID reasonId, @Size(max = 2000) String note) {}
