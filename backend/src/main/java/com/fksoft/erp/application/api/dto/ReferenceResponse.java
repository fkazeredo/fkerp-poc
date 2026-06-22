package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.reference.ReferenceData;
import java.util.UUID;

/**
 * Reference-data value response.
 *
 * @param id the id
 * @param code the stable code
 * @param label the display label
 * @param active whether the value is active
 * @param sortOrder the sort order
 */
public record ReferenceResponse(UUID id, String code, String label, boolean active, int sortOrder) {

    public static ReferenceResponse from(ReferenceData value) {
        return new ReferenceResponse(value.id(), value.code(), value.label(), value.active(), value.sortOrder());
    }
}
