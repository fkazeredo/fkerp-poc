package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: how a Lead entered the commercial funnel (managed cadastro). */
@Entity
@Table(name = "origins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Origin extends ReferenceData {

    /**
     * Creates a new active Origin.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new Origin
     */
    public static Origin create(String code, String label, int sortOrder) {
        Origin origin = new Origin();
        origin.init(code, label, sortOrder);
        return origin;
    }
}
