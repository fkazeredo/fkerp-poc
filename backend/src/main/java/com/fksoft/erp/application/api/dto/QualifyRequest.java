package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to qualify a Lead.
 *
 * @param mainInterest the main commercial interest (required)
 * @param note optional commercial note
 */
public record QualifyRequest(
        @NotBlank(message = "Interesse principal é obrigatório") @Size(max = 500, message = "Interesse muito longo")
                String mainInterest,
        @Size(max = 2000, message = "Anotação muito longa") String note) {}
