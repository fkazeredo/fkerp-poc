package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal is requested from an Opportunity that is not READY_FOR_PROPOSAL. */
public class OpportunityNotReadyForProposalException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpportunityNotReadyForProposalException() {
        super("proposal.opportunity-not-ready");
    }
}
