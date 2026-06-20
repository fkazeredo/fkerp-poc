package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal that is not Approved is marked as sent to the client. */
public class ProposalNotApprovedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalNotApprovedException() {
        super("proposal.not-approved");
    }
}
