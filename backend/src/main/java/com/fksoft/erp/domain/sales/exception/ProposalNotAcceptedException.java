package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Commercial Order is created from a Proposal that is not Accepted. */
public class ProposalNotAcceptedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalNotAcceptedException() {
        super("proposal.not-accepted");
    }
}
