package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal without a responsible person is submitted for review. */
public class ProposalResponsibleRequiredException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalResponsibleRequiredException() {
        super("proposal.responsible-required");
    }
}
