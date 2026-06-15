package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when marking a Lead as lost with a loss reason that is unknown or inactive. */
public class LossReasonNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LossReasonNotAvailableException() {
        super("lead.loss-reason-not-available");
    }
}
