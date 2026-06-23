package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to create a Proposal item type cadastro value.
 *
 * @param code stable code (uppercase letters, digits and underscore)
 * @param label display label
 * @param sortOrder sort order
 * @param requiresBooking whether items of this type require a booking operation
 */
public record ProposalItemTypeCreateRequest(
        @NotBlank(message = "Código é obrigatório")
                @Pattern(regexp = "[A-Z0-9_]+", message = "Código deve usar MAIÚSCULAS, dígitos ou _")
                String code,
        @NotBlank(message = "Descrição é obrigatória") String label,
        int sortOrder,
        boolean requiresBooking) {}
