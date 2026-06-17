package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal item does not exist on the given Proposal. */
public class ProposalItemNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalItemNotFoundException() {
        super("proposal.item-not-found");
    }
}
