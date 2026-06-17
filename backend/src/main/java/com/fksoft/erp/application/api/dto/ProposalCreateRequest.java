package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create a Proposal from a READY_FOR_PROPOSAL Opportunity. The source Opportunity (and through
 * it, the Lead and origin) and, by default, the responsible are taken from the Opportunity.
 *
 * @param opportunityId the source (ready-for-proposal) opportunity id (required)
 * @param responsiblePersonId optional responsible override (defaults to the opportunity's responsible)
 * @param title the client-facing title / summary (required)
 * @param notes optional proposal notes
 * @param validUntil optional validity date
 * @param commercialTerms optional initial commercial terms
 */
public record ProposalCreateRequest(
        @NotNull UUID opportunityId,
        UUID responsiblePersonId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String notes,
        LocalDate validUntil,
        @Size(max = 4000) String commercialTerms) {}
