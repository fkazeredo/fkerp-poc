package com.fksoft.erp.infra.security;

import com.fksoft.erp.domain.identity.AuthenticatedUser;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues and classifies the application's own JWTs (access + refresh), signed with HMAC. */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String SCOPE_CLAIM = "scope";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties props;

    /**
     * Issues a signed access token (subject = user id, scopes in the {@code scope} claim).
     *
     * @param user the authenticated user
     * @return the encoded JWT access token
     */
    public String issueAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTokenTtl()))
                .claim(SCOPE_CLAIM, String.join(" ", user.scopes()))
                .claim(TOKEN_TYPE_CLAIM, ACCESS)
                .build();
        return encode(claims);
    }

    /**
     * Issues a signed refresh token (subject = user id).
     *
     * @param userId the user id
     * @return the encoded JWT refresh token
     */
    public String issueRefreshToken(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().refreshTokenTtl()))
                .claim(TOKEN_TYPE_CLAIM, REFRESH)
                .build();
        return encode(claims);
    }

    public boolean isRefreshToken(Jwt jwt) {
        return REFRESH.equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM));
    }

    public long accessTokenTtlSeconds() {
        return props.jwt().accessTokenTtl().toSeconds();
    }

    public Duration refreshTokenTtl() {
        return props.jwt().refreshTokenTtl();
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
