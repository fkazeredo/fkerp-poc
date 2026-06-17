package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a user tries to access a Proposal they are not allowed to see. */
public class ProposalAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalAccessDeniedException() {
        super("proposal.access-denied");
    }
}
