package com.unbutu.ticket.identity.security;

import com.unbutu.ticket.identity.google.GoogleLoginSecurity;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, GoogleLoginSecurity google) throws Exception {
        google.configure(http);
        return http
                // API không dùng session; CSRF: endpoint dùng cookie (refresh/logout) được SameSite=Lax bảo vệ.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/identity/register", "/api/identity/login",
                                "/api/identity/refresh", "/api/identity/logout", "/api/identity/providers").permitAll()
                        .requestMatchers(GoogleLoginSecurity.AUTHORIZATION_BASE + "/**",
                                GoogleLoginSecurity.CALLBACK_BASE + "/**").permitAll()
                        .requestMatchers("/.well-known/jwks.json").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                // API luôn trả 401 (không redirect sang trang login Google kể cả khi trình duyệt gọi)
                .exceptionHandling(e -> e.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
                .build();
    }

    /** bcrypt (mặc định), lưu kèm tiền tố {bcrypt} → sau này đổi thuật toán không phải migrate hash cũ. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
