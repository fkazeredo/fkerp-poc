package com.fksoft.erp.domain.crm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: reason a Lead may be marked Lost (managed cadastro). */
@Entity
@Table(name = "loss_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LossReason extends ReferenceData {

    /**
     * Creates a new active LossReason.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new LossReason
     */
    public static LossReason create(String code, String label, int sortOrder) {
        LossReason reason = new LossReason();
        reason.init(code, label, sortOrder);
        return reason;
    }
}
