package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal that is not Sent is accepted or rejected on behalf of the client. */
public class ProposalNotSentException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalNotSentException() {
        super("proposal.not-sent");
    }
}
