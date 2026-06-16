package com.fksoft.erp.domain.crm.dto;

/**
 * Input for creating a reference-data value.
 *
 * @param code stable code (unique per cadastro)
 * @param label display label
 * @param sortOrder sort order
 */
public record ReferenceCommand(String code, String label, int sortOrder) {}
