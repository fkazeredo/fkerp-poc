package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal whose total is not positive is submitted for review. */
public class ProposalTotalRequiredException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalTotalRequiredException() {
        super("proposal.total-required");
    }
}
