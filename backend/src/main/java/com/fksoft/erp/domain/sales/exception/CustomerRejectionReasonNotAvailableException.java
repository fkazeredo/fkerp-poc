package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when declining a Proposal with an unknown or inactive CustomerRejectionReason. */
public class CustomerRejectionReasonNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CustomerRejectionReasonNotAvailableException() {
        super("proposal.customer-rejection-reason-not-available");
    }
}
