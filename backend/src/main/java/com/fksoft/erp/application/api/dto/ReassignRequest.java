package com.fksoft.erp.application.api.dto;

import java.util.UUID;

/**
 * Request to reassign a Lead's responsible person.
 *
 * @param responsiblePersonId the new responsible user id, or {@code null} to unassign
 */
public record ReassignRequest(UUID responsiblePersonId) {}
