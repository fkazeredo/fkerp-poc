package com.fksoft.erp.domain.crm;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: result of a Lead interaction (managed cadastro). */
@Entity
@Table(name = "interaction_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionResult extends ReferenceData {

    /**
     * Creates a new active InteractionResult.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new InteractionResult
     */
    public static InteractionResult create(String code, String label, int sortOrder) {
        InteractionResult result = new InteractionResult();
        result.init(code, label, sortOrder);
        return result;
    }
}
