package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a Proposal-level discount is invalid — type and value must be present together, a percentage
 * must be 0–100, and an absolute amount must be between 0 and the items subtotal (so the total never goes
 * negative).
 */
public class ProposalDiscountInvalidException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalDiscountInvalidException() {
        super("proposal.discount-invalid");
    }
}
