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

    // Same three-tier model for Proposals (Sales & Proposals); ProposalAccessPolicy narrows visibility.
    private static final String[] PROPOSAL_READ_SCOPES = {
        "SCOPE_sales:proposal:read", "SCOPE_sales:proposal:read:unassigned", "SCOPE_sales:proposal:read:all"
    };

    // Same three-tier model for Commercial Orders (Sales & Proposals); OrderAccessPolicy narrows visibility.
    private static final String[] ORDER_READ_SCOPES = {
        "SCOPE_sales:order:read", "SCOPE_sales:order:read:unassigned", "SCOPE_sales:order:read:all"
    };

    // Same three-tier model for Booking Requests (Booking Operations); BookingRequestAccessPolicy narrows
    // visibility. Any read tier passes the GET gate.
    private static final String[] BOOKING_READ_SCOPES = {
        "SCOPE_booking:request:read", "SCOPE_booking:request:read:unassigned", "SCOPE_booking:request:read:all"
    };

    // Two-tier read model for Receivables (Financial Operations); ReceivableAccessPolicy narrows WHICH are
    // visible (own = financial responsible / all). Any read tier passes the GET gate.
    private static final String[] FINANCIAL_READ_SCOPES = {
        "SCOPE_financial:receivable:read", "SCOPE_financial:receivable:read:all"
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
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/opportunities/*/lose",
                                "/api/opportunities/*/stage",
                                "/api/opportunities/*/activities")
                        .hasAuthority("SCOPE_crm:opportunity:update")
                        .requestMatchers(HttpMethod.PUT, "/api/opportunities/*")
                        .hasAuthority("SCOPE_crm:opportunity:update")
                        .requestMatchers(HttpMethod.GET, "/api/opportunities", "/api/opportunities/**")
                        .hasAnyAuthority(OPPORTUNITY_READ_SCOPES)
                        .requestMatchers(HttpMethod.POST, "/api/proposals")
                        .hasAuthority("SCOPE_sales:proposal:create")
                        .requestMatchers(HttpMethod.POST, "/api/proposals/*/items")
                        .hasAuthority("SCOPE_sales:proposal:update")
                        .requestMatchers(HttpMethod.PUT, "/api/proposals/*/items/*")
                        .hasAuthority("SCOPE_sales:proposal:update")
                        .requestMatchers(HttpMethod.DELETE, "/api/proposals/*/items/*")
                        .hasAuthority("SCOPE_sales:proposal:update")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/proposals/*/submit",
                                "/api/proposals/*/send",
                                "/api/proposals/*/accept",
                                "/api/proposals/*/decline")
                        .hasAuthority("SCOPE_sales:proposal:update")
                        .requestMatchers(HttpMethod.POST, "/api/proposals/*/approve", "/api/proposals/*/reject")
                        .hasAuthority("SCOPE_sales:proposal:approve")
                        .requestMatchers(HttpMethod.PUT, "/api/proposals/*")
                        .hasAuthority("SCOPE_sales:proposal:update")
                        .requestMatchers(HttpMethod.GET, "/api/proposals", "/api/proposals/**")
                        .hasAnyAuthority(PROPOSAL_READ_SCOPES)
                        .requestMatchers(HttpMethod.POST, "/api/orders")
                        .hasAuthority("SCOPE_sales:order:create")
                        .requestMatchers(HttpMethod.GET, "/api/orders", "/api/orders/**")
                        .hasAnyAuthority(ORDER_READ_SCOPES)
                        .requestMatchers(HttpMethod.POST, "/api/bookings")
                        .hasAuthority("SCOPE_booking:request:create")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/bookings/*/attempts",
                                "/api/bookings/*/items/*/confirm",
                                "/api/bookings/*/items/*/confirm-car-rental",
                                "/api/bookings/*/items/*/fail")
                        .hasAuthority("SCOPE_booking:request:update")
                        .requestMatchers(HttpMethod.GET, "/api/bookings", "/api/bookings/**")
                        .hasAnyAuthority(BOOKING_READ_SCOPES)
                        .requestMatchers(HttpMethod.POST, "/api/receivables")
                        .hasAuthority("SCOPE_financial:receivable:create")
                        .requestMatchers(HttpMethod.POST, "/api/receivables/*/installments/*/payments")
                        .hasAuthority("SCOPE_financial:payment:register")
                        .requestMatchers(HttpMethod.GET, "/api/receivables", "/api/receivables/**")
                        .hasAnyAuthority(FINANCIAL_READ_SCOPES)
                        .requestMatchers(HttpMethod.GET, "/api/crm/responsibles")
                        .hasAnyAuthority(READ_SCOPES)
                        .requestMatchers(HttpMethod.GET, "/api/crm/**")
                        .authenticated()
                        .requestMatchers("/api/crm/**")
                        .hasAuthority("SCOPE_reference:manage")
                        // Sales, Booking & Financial cadastros (reference data) — read = authenticated, write =
                        // reference:manage. These paths (singular) are disjoint from the operational /api/proposals,
                        // /api/orders, /api/bookings and /api/receivables endpoints matched above.
                        .requestMatchers(HttpMethod.GET, "/api/sales/**", "/api/booking/**", "/api/financial/**")
                        .authenticated()
                        .requestMatchers("/api/sales/**", "/api/booking/**", "/api/financial/**")
                        .hasAuthority("SCOPE_reference:manage")
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
