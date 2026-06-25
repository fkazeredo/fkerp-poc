package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request to approve an Eligible Commission. The approval notes are optional.
 *
 * @param notes optional approval notes (max 2000 chars)
 */
public record ApproveCommissionRequest(@Size(max = 2000) String notes) {}
