package com.fksoft.erp.infra.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security configuration (prefix {@code app.security}): JWT signing/lifetimes, the allowed CORS
 * origin and the refresh-token cookie.
 *
 * @param jwt JWT signing secret and token lifetimes
 * @param allowedOrigin the frontend origin allowed by CORS (credentials enabled)
 * @param refreshCookie the httpOnly refresh-token cookie settings
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt, String allowedOrigin, RefreshCookie refreshCookie) {

    /**
     * JWT signing/lifetime settings.
     *
     * @param secret HMAC secret (>= 32 bytes for HS256)
     * @param accessTokenTtl access-token lifetime
     * @param refreshTokenTtl refresh-token lifetime
     */
    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    /**
     * Refresh-token cookie settings.
     *
     * @param name cookie name
     * @param secure whether the cookie is marked Secure (true in production/HTTPS)
     */
    public record RefreshCookie(String name, boolean secure) {}
}
