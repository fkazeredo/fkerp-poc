package com.fksoft.erp.domain.identity;

import com.fksoft.erp.domain.error.DomainException;
import java.io.Serial;

/** Raised when username/password authentication fails or the user is inactive. */
public class InvalidCredentialsException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("auth.invalid-credentials");
    }
}
