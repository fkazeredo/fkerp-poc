package com.fksoft.erp.domain.crm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: type of a Lead interaction (managed cadastro). */
@Entity
@Table(name = "interaction_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionType extends ReferenceData {

    /** Stable code of the built-in "internal note" type used by the initial note. */
    public static final String INTERNAL_NOTE_CODE = "INTERNAL_NOTE";

    /**
     * Creates a new active InteractionType.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new InteractionType
     */
    public static InteractionType create(String code, String label, int sortOrder) {
        InteractionType type = new InteractionType();
        type.init(code, label, sortOrder);
        return type;
    }
}
