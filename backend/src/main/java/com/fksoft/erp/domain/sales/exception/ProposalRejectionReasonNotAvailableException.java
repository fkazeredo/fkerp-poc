package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when rejecting a Proposal with an unknown or inactive ProposalRejectionReason. */
public class ProposalRejectionReasonNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalRejectionReasonNotAvailableException() {
        super("proposal.rejection-reason-not-available");
    }
}
