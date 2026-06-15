package com.fksoft.erp.infra.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the current authenticated user from the security context. Application Services never
 * touch {@code SecurityContextHolder} directly; the delivery layer uses this provider.
 */
@Component
public class UserContextProvider {

    /**
     * Returns the id (JWT subject) of the currently authenticated user.
     *
     * @return the current user id
     * @throws IllegalStateException if there is no authenticated JWT in the context
     */
    public UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated user in security context");
    }
}
