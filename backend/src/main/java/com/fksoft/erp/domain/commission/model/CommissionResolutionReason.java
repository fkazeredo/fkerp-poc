package com.fksoft.erp.domain.commission.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reference data: the reason a Commission is rejected or cancelled (a single shared, managed cadastro for both the
 * reject and the cancel actions). Informational only — voiding a commission changes no Order, Receivable or financial
 * data.
 */
@Entity
@Table(name = "commission_resolution_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionResolutionReason extends ReferenceData {

    /**
     * Creates a new active CommissionResolutionReason.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new CommissionResolutionReason
     */
    public static CommissionResolutionReason create(String code, String label, int sortOrder) {
        CommissionResolutionReason reason = new CommissionResolutionReason();
        reason.init(code, label, sortOrder);
        return reason;
    }
}
