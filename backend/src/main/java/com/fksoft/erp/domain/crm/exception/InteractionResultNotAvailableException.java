package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering an interaction with an unknown or inactive InteractionResult. */
public class InteractionResultNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InteractionResultNotAvailableException() {
        super("interaction.result-not-available");
    }
}
