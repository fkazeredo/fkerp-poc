package com.fksoft.erp.domain.crm.model;

import java.util.UUID;

/**
 * Domain event published when an Opportunity is created from a Qualified Lead.
 *
 * @param opportunityId the new opportunity id
 * @param leadId the source lead id
 * @param createdBy id of the user who created the opportunity
 * @param responsiblePersonId responsible user id, or null
 */
public record OpportunityCreated(UUID opportunityId, UUID leadId, UUID createdBy, UUID responsiblePersonId) {}
