package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when using an unknown or inactive ProposalItemType. */
public class ProposalItemTypeNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalItemTypeNotAvailableException() {
        super("proposal.item-type-not-available");
    }
}
