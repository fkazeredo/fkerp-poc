package com.fksoft.erp.domain.identity;

/** Port for password verification; the implementation (BCrypt) lives in {@code infra.security}. */
public interface PasswordHasher {

    boolean matches(String rawPassword, String passwordHash);
}
