package com.fksoft.erp.infra.security;

import com.fksoft.erp.domain.identity.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt adapter for the {@link PasswordHasher} port. */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
