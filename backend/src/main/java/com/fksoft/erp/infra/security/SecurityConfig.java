package com.fksoft.erp.infra.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless security: the app is a Resource Server validating its own HMAC-signed access tokens.
 * Creating a Lead requires the {@code crm:lead:create} scope; {@code /api/auth/**} and actuator are open.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String HMAC = "HmacSHA256";

    // Any read tier grants access to the list/detail read endpoints; the LeadAccessPolicy then
    // narrows WHICH Leads are visible (own only / own + pool / all).
    private static final String[] READ_SCOPES = {
        "SCOPE_crm:lead:read", "SCOPE_crm:lead:read:unassigned", "SCOPE_crm:lead:read:all"
    };

    // Same three-tier model for Opportunities; OpportunityAccessPolicy narrows WHICH are visible.
    private static final String[] OPPORTUNITY_READ_SCOPES = {
        "SCOPE_crm:opportunity:read", "SCOPE_crm:opportunity:read:unassigned", "SCOPE_crm:opportunity:read:all"
    };

    private final SecurityProperties props;

    @Bean
    SecretKeySpec jwtSecretKey() {
        return new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key) {
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("SCOPE_");
        authorities.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**", "/api/auth/**", "/api/version")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/leads")
                        .hasAuthority("SCOPE_crm:lead:create")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/leads/*/qualify",
                                "/api/leads/*/lose",
                                "/api/leads/*/reassign",
                                "/api/leads/*/interactions")
                        .hasAuthority("SCOPE_crm:lead:update")
                        .requestMatchers(HttpMethod.GET, "/api/leads", "/api/leads/**")
                        .hasAnyAuthority(READ_SCOPES)
                        .requestMatchers(HttpMethod.POST, "/api/opportunities")
                        .hasAuthority("SCOPE_crm:opportunity:create")
                        .requestMatchers(HttpMethod.POST, "/api/opportunities/*/lose")
                        .hasAuthority("SCOPE_crm:opportunity:update")
                        .requestMatchers(HttpMethod.GET, "/api/opportunities", "/api/opportunities/**")
                        .hasAnyAuthority(OPPORTUNITY_READ_SCOPES)
                        .requestMatchers(HttpMethod.GET, "/api/crm/responsibles")
                        .hasAnyAuthority(READ_SCOPES)
                        .requestMatchers(HttpMethod.GET, "/api/crm/**")
                        .authenticated()
                        .requestMatchers("/api/crm/**")
                        .hasAuthority("SCOPE_crm:reference:manage")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(props.allowedOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
