package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal without any items is submitted for review. */
public class ProposalHasNoItemsException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalHasNoItemsException() {
        super("proposal.no-items");
    }
}
