package com.fksoft.erp.domain.sales.model;

import java.util.UUID;

/**
 * Domain event published when a Proposal is created from a READY_FOR_PROPOSAL Opportunity.
 *
 * @param proposalId the new proposal id
 * @param opportunityId the source opportunity id
 * @param leadId the source lead id (kept for traceability)
 * @param createdBy id of the user who created the proposal
 * @param responsiblePersonId responsible user id, or null
 */
public record ProposalCreated(
        UUID proposalId, UUID opportunityId, UUID leadId, UUID createdBy, UUID responsiblePersonId) {}
