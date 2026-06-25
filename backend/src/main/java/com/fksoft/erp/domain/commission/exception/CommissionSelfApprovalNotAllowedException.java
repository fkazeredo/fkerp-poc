package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a beneficiary tries to approve their own Commission (self-approval is not allowed). */
public class CommissionSelfApprovalNotAllowedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionSelfApprovalNotAllowedException() {
        super("commission.self-approval-not-allowed");
    }
}
