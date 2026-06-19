package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Proposal that is not in Ready for Review is approved or rejected. */
public class ProposalNotUnderReviewException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalNotUnderReviewException() {
        super("proposal.not-under-review");
    }
}
