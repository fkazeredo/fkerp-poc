package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when qualifying a Lead that has no responsible person. */
public class LeadQualificationRequiresResponsibleException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadQualificationRequiresResponsibleException() {
        super("lead.qualification-requires-responsible");
    }
}
