package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: type of a commercial activity registered on an Opportunity (managed cadastro). */
@Entity
@Table(name = "opportunity_activity_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityActivityType extends ReferenceData {

    /**
     * Creates a new active OpportunityActivityType.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new OpportunityActivityType
     */
    public static OpportunityActivityType create(String code, String label, int sortOrder) {
        OpportunityActivityType type = new OpportunityActivityType();
        type.init(code, label, sortOrder);
        return type;
    }
}
