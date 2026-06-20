package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.sales.model.CustomerRejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body to register that the client rejected a sent Proposal. The reason is required (a fixed customer
 * set); the note is an optional free-text detail.
 *
 * @param reason the customer-rejection reason (required)
 * @param note an optional free-text note
 */
public record DeclineProposalRequest(@NotNull CustomerRejectionReason reason, @Size(max = 2000) String note) {}
