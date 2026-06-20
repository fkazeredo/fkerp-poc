package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body to register that the client accepted a sent Proposal. The confirmation note is optional
 * free-text. Acceptance triggers no real integration and creates no Booking, Financial, Commission or
 * Commercial Order data.
 *
 * @param note an optional client confirmation note
 */
public record AcceptProposalRequest(@Size(max = 2000) String note) {}
