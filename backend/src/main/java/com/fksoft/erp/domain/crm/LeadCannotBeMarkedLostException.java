package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Lead cannot be marked as lost from its current status (already lost). */
public class LeadCannotBeMarkedLostException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadCannotBeMarkedLostException() {
        super("lead.cannot-mark-lost");
    }
}
