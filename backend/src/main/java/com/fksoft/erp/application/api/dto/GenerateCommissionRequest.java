package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to generate an Expected Commission from a Commercial Order.
 *
 * @param commercialOrderId the source Commercial Order id
 */
public record GenerateCommissionRequest(@NotNull UUID commercialOrderId) {}
