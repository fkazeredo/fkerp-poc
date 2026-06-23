package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to update a Proposal item type cadastro value (code is immutable).
 *
 * @param label display label
 * @param sortOrder sort order
 * @param active active flag (false soft-deletes)
 * @param requiresBooking whether items of this type require a booking operation
 */
public record ProposalItemTypeUpdateRequest(
        @NotBlank(message = "Descrição é obrigatória") String label,
        int sortOrder,
        boolean active,
        boolean requiresBooking) {}
