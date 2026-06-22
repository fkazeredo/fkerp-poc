package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: outcome of a commercial activity registered on an Opportunity (managed cadastro). */
@Entity
@Table(name = "opportunity_activity_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityActivityResult extends ReferenceData {

    /**
     * Creates a new active OpportunityActivityResult.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new OpportunityActivityResult
     */
    public static OpportunityActivityResult create(String code, String label, int sortOrder) {
        OpportunityActivityResult result = new OpportunityActivityResult();
        result.init(code, label, sortOrder);
        return result;
    }
}
