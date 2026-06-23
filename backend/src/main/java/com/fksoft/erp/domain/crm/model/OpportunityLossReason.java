package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reference data: reason an Opportunity can be marked as lost (managed cadastro — a commercial set, distinct
 * from the Lead's contact-oriented loss reasons).
 */
@Entity
@Table(name = "opportunity_loss_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityLossReason extends ReferenceData {

    /**
     * Creates a new active OpportunityLossReason.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new OpportunityLossReason
     */
    public static OpportunityLossReason create(String code, String label, int sortOrder) {
        OpportunityLossReason reason = new OpportunityLossReason();
        reason.init(code, label, sortOrder);
        return reason;
    }
}
