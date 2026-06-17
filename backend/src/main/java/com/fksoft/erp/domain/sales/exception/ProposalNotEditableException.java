package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal is edited (e.g. its items) while it is not a Draft. */
public class ProposalNotEditableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalNotEditableException() {
        super("proposal.not-editable");
    }
}
