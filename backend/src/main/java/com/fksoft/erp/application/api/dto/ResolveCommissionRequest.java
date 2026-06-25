package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body to reject or cancel a Commission. The resolution-reason cadastro id is required; the note is optional
 * free text. The same body shape serves both the reject and the cancel endpoints.
 *
 * @param reasonId the resolution-reason cadastro id (required)
 * @param note an optional free-text note
 */
public record ResolveCommissionRequest(@NotNull UUID reasonId, @Size(max = 2000) String note) {}
