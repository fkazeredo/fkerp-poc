package com.fksoft.erp.infra.web;

import java.util.List;

/**
 * Predictable API error body shared by the whole application: {@code {code, message, fields}}.
 *
 * @param code stable error code (i18n key)
 * @param message resolved, human-readable message
 * @param fields per-field details (validation errors or domain {@code ErrorDetails})
 */
public record ApiErrorResponse(String code, String message, List<FieldError> fields) {

    /**
     * A single field-level error.
     *
     * @param field field name (or object name for class-level errors)
     * @param message field message
     */
    public record FieldError(String field, String message) {}
}
