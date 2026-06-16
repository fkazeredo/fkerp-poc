package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a user tries to access or act on a Lead they are not allowed to see. */
public class LeadAccessDeniedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadAccessDeniedException() {
        super("lead.access-denied");
    }
}
