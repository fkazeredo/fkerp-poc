package com.fksoft.erp.domain.sales.model;

import java.util.UUID;

/**
 * Domain event published when a Commercial Order is created from an Accepted Proposal. A business fact other
 * modules may react to later (booking, finance) — it carries no transport concern.
 *
 * @param orderId the new Commercial Order id
 * @param proposalId the source Proposal id
 * @param opportunityId the source Opportunity id (now marked won)
 * @param leadId the source Lead id
 * @param createdBy id of the user who created the Order
 * @param responsiblePersonId the responsible person preserved from the Proposal
 */
public record CommercialOrderCreated(
        UUID orderId, UUID proposalId, UUID opportunityId, UUID leadId, UUID createdBy, UUID responsiblePersonId) {}
