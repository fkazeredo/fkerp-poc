package com.fksoft.erp.domain.error;

import java.io.Serial;

/**
 * Base type for business (domain) errors. Carries a stable {@code code} (also the i18n message key)
 * and optional message arguments. Domain exceptions hold no transport concern: no HTTP status, no
 * response DTO. The presentation layer maps them to HTTP.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;
    private final transient Object[] args;

    /**
     * Creates a domain exception.
     *
     * @param code stable error code, equal to the i18n message key
     * @param args optional message arguments
     */
    protected DomainException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args != null ? args.clone() : new Object[0];
    }

    public String code() {
        return code;
    }

    public Object[] args() {
        return args.clone();
    }
}
