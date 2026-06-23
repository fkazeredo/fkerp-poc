package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body to register that the client rejected a sent Proposal. The reason cadastro id is required; the
 * note is an optional free-text detail.
 *
 * @param reasonId the customer-rejection-reason cadastro id (required; an unknown/inactive value is 422)
 * @param note an optional free-text note
 */
public record DeclineProposalRequest(@NotNull UUID reasonId, @Size(max = 2000) String note) {}
