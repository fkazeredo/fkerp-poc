package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body to create a Commercial Order from an Accepted Proposal.
 *
 * @param proposalId the source (Accepted) proposal id
 */
public record CreateOrderRequest(@NotNull UUID proposalId) {}
