package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.LoginRequest;
import com.fksoft.erp.application.api.dto.TokenResponse;
import com.fksoft.erp.domain.identity.AuthenticatedUser;
import com.fksoft.erp.domain.identity.IdentityService;
import com.fksoft.erp.domain.identity.InvalidCredentialsException;
import com.fksoft.erp.infra.security.SecurityProperties;
import com.fksoft.erp.infra.security.TokenService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints: login (issues access + refresh), refresh and logout. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IdentityService identity;
    private final TokenService tokens;
    private final JwtDecoder jwtDecoder;
    private final SecurityProperties props;

    /** Authenticates and returns an access token; the refresh token is set as an httpOnly cookie. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = identity.authenticate(request.username(), request.password());
        String refresh = tokens.issueRefreshToken(user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refresh).toString())
                .body(new TokenResponse(tokens.issueAccessToken(user), "Bearer", tokens.accessTokenTtlSeconds()));
    }

    /** Issues a new access token from a valid refresh-token cookie. */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${app.security.refresh-cookie.name}", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidCredentialsException();
        }
        Jwt jwt = decodeRefresh(refreshToken);
        AuthenticatedUser user = identity.requireActive(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(
                new TokenResponse(tokens.issueAccessToken(user), "Bearer", tokens.accessTokenTtlSeconds()));
    }

    /** Clears the refresh-token cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = baseCookie("").maxAge(0).build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    private Jwt decodeRefresh(String refreshToken) {
        try {
            Jwt jwt = jwtDecoder.decode(refreshToken);
            if (!tokens.isRefreshToken(jwt)) {
                throw new InvalidCredentialsException();
            }
            return jwt;
        } catch (JwtException e) {
            throw new InvalidCredentialsException();
        }
    }

    private ResponseCookie refreshCookie(String value) {
        return baseCookie(value).maxAge(tokens.refreshTokenTtl()).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(props.refreshCookie().name(), value)
                .httpOnly(true)
                .secure(props.refreshCookie().secure())
                .sameSite("Strict")
                .path("/api/auth/refresh");
    }
}
