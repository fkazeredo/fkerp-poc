package com.fksoft.erp.application.api.dto;

/**
 * Access-token response. The refresh token is delivered as an httpOnly cookie, never in the body.
 *
 * @param accessToken the JWT access token
 * @param tokenType the token type ("Bearer")
 * @param expiresInSeconds access-token lifetime in seconds
 */
public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}
