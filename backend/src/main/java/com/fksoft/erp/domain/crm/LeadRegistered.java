package com.fksoft.erp.domain.crm;

import java.util.UUID;

/**
 * Domain event published when a Lead is registered. Enables the Sprint 2 opportunity handoff without
 * coupling CRM to downstream contexts.
 *
 * @param leadId the new lead id
 * @param originId the origin cadastro id
 * @param registeredBy id of the user who registered the lead
 * @param responsiblePersonId responsible user id, or null when unassigned
 */
public record LeadRegistered(UUID leadId, UUID originId, UUID registeredBy, UUID responsiblePersonId) {}
