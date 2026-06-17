package com.fksoft.erp.domain.sales.service.data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input for creating a Proposal from a READY_FOR_PROPOSAL Opportunity. Built by the delivery layer after
 * boundary validation.
 *
 * @param opportunityId the source (ready-for-proposal) opportunity id (required)
 * @param responsiblePersonId responsible user id; when null, the opportunity's responsible is kept by default
 * @param title the client-facing title / summary (required)
 * @param notes optional proposal notes
 * @param validUntil optional validity date
 * @param commercialTerms optional initial commercial terms
 */
public record CreateProposalCommand(
        UUID opportunityId,
        UUID responsiblePersonId,
        String title,
        String notes,
        LocalDate validUntil,
        String commercialTerms) {}
