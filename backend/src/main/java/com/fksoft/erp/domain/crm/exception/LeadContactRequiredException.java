package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when a Lead is created without any contact method (phone, WhatsApp or e-mail). */
public class LeadContactRequiredException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadContactRequiredException() {
        super("lead.contact-required");
    }
}
