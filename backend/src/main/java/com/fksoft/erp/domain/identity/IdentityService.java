package com.fksoft.erp.domain.identity;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity application service: authenticates users so the security layer can issue tokens. Within
 * the {@code domain} package other areas may use Identity types directly (no Facade pattern).
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    /**
     * Authenticates by username/password.
     *
     * @param username the username
     * @param rawPassword the raw password
     * @return the authenticated user with its scopes
     * @throws InvalidCredentialsException if credentials are invalid or the user is inactive
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String username, String rawPassword) {
        User user = users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!user.active() || !passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthenticatedUser(user.id(), user.username(), Set.copyOf(user.scopes()));
    }

    /**
     * Loads an active user by id (used when refreshing tokens).
     *
     * @param id the user id
     * @return the authenticated user with current scopes
     * @throws InvalidCredentialsException if the user is missing or inactive
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser requireActive(UUID id) {
        User user = users.findById(id).filter(User::active).orElseThrow(InvalidCredentialsException::new);
        return new AuthenticatedUser(user.id(), user.username(), Set.copyOf(user.scopes()));
    }
}
