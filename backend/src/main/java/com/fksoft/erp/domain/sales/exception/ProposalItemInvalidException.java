package com.fksoft.erp.domain.sales.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when a Proposal item's data is invalid beyond the field-level constraints — specifically an
 * inconsistent discount (type and value must be present together, a percentage must be 0–100, and an
 * absolute amount must not exceed the line subtotal).
 */
public class ProposalItemInvalidException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProposalItemInvalidException() {
        super("proposal.item-invalid");
    }
}
