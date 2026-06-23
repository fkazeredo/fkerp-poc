package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.reference.ReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reference data: reason a Proposal can be rejected at internal review (managed cadastro). */
@Entity
@Table(name = "proposal_rejection_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalRejectionReason extends ReferenceData {

    /**
     * Creates a new active ProposalRejectionReason.
     *
     * @param code stable code
     * @param label display label
     * @param sortOrder sort order
     * @return the new ProposalRejectionReason
     */
    public static ProposalRejectionReason create(String code, String label, int sortOrder) {
        ProposalRejectionReason reason = new ProposalRejectionReason();
        reason.init(code, label, sortOrder);
        return reason;
    }
}
