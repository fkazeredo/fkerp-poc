package com.fksoft.erp.domain.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Result of a successful authentication: the user identity and its granted scopes.
 *
 * @param id user id (becomes the JWT subject)
 * @param username the username
 * @param scopes granted scopes
 */
public record AuthenticatedUser(UUID id, String username, Set<String> scopes) {}
