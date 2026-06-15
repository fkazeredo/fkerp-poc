package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request to qualify a Lead.
 *
 * @param note optional qualification note
 */
public record QualifyRequest(@Size(max = 2000, message = "Anotação muito longa") String note) {}
