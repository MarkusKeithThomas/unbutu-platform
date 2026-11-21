package com.unbutu.ticket.booking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Mọi API đơn hàng cần access token của identity (verify local bằng JWKS – không gọi identity mỗi request).
 * userId = claim {@code sub}. Gateway verify JWT (P0) làm sau; service vẫn giữ kiểm tra (defense in depth).
 */
@Configuration
@EnableConfigurationProperties(SecurityConfig.JwtProps.class)
class SecurityConfig {

    @ConfigurationProperties("booking.jwt")
    record JwtProps(String jwkSetUri, String issuer) {
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())   // chỉ Bearer token trong header
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/bookings/**", "/api/bookings").authenticated()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProps props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                jwt -> "access".equals(jwt.getClaimAsString("token_use"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "token_use must be access", null))));
        return decoder;
    }
}
