package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal without a validity date is submitted for review. */
public class ProposalValidityRequiredException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalValidityRequiredException() {
        super("proposal.validity-required");
    }
}
