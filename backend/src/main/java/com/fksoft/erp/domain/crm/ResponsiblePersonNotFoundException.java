package com.fksoft.erp.domain.crm;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Lead is given a responsible person that is unknown or inactive. */
public class ResponsiblePersonNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResponsiblePersonNotFoundException() {
        super("lead.responsible-not-found");
    }
}
