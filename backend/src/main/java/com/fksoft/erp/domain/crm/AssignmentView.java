package com.fksoft.erp.domain.crm;

import java.time.Instant;

/** Read view of a single assignment-history entry of a Lead. */
public record AssignmentView(
        String fromResponsibleName, String toResponsibleName, String assignedByName, Instant assignedAt) {}
