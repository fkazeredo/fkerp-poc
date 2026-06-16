package com.fksoft.erp.domain.crm.exception;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when registering an interaction with an unknown or inactive InteractionType. */
public class InteractionTypeNotAvailableException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InteractionTypeNotAvailableException() {
        super("interaction.type-not-available");
    }
}
