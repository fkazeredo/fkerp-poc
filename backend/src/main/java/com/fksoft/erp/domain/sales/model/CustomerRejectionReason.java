package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reference data: reason a client can reject a sent Proposal (managed cadastro — a commercial set distinct
 * from the internal-review {@link ProposalRejectionReason}).
 */
@Entity
@Table(name = "customer_rejection_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRejectionReason extends ReferenceData {

    /**
     * Creates a new active CustomerRejectionReason.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new CustomerRejectionReason
     */
    public static CustomerRejectionReason create(String code, String label, int sortOrder) {
        CustomerRejectionReason reason = new CustomerRejectionReason();
        reason.init(code, label, sortOrder);
        return reason;
    }
}
