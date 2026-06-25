package com.fksoft.erp.domain.commission.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/**
 * Raised when cancelling a Commission that cannot be cancelled — only an Expected or an Approved-but-unpaid
 * commission may be cancelled (a Paid commission cannot be cancelled through the ordinary flow).
 */
public class CommissionNotCancellableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionNotCancellableException() {
        super("commission.not-cancellable");
    }
}
