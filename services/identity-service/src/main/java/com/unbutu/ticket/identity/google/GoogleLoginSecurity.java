package com.unbutu.ticket.identity.google;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * Bật login Google trên SecurityFilterChain nếu có GOOGLE_CLIENT_ID.
 * <ul>
 *   <li>Bắt đầu: {@code GET /api/identity/oauth2/authorization/google} → 302 sang Google</li>
 *   <li>Google gọi lại: {@code GET /api/identity/login/oauth2/code/google?code=…&state=…}</li>
 * </ul>
 * Cả hai nằm dưới /api/identity/** → đi qua gateway như API thường.
 */
@Component
public class GoogleLoginSecurity {

    public static final String AUTHORIZATION_BASE = "/api/identity/oauth2/authorization";
    public static final String CALLBACK_BASE = "/api/identity/login/oauth2/code";

    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final StateCookieAuthorizationRequestRepository requestRepository;
    private final GoogleLoginHandlers handlers;

    GoogleLoginSecurity(ObjectProvider<ClientRegistrationRepository> registrations,
                        StateCookieAuthorizationRequestRepository requestRepository, GoogleLoginHandlers handlers) {
        this.registrations = registrations;
        this.requestRepository = requestRepository;
        this.handlers = handlers;
    }

    public void configure(HttpSecurity http) throws Exception {
        ClientRegistrationRepository repo = registrations.getIfAvailable();
        if (repo == null) {
            return;   // chưa cấu hình Google → không bật
        }
        http.oauth2Login(o -> o
                .clientRegistrationRepository(repo)
                .loginPage(AUTHORIZATION_BASE + "/" + GoogleClientConfig.REGISTRATION_ID)   // không sinh trang /login mặc định
                .authorizationEndpoint(a -> a.baseUri(AUTHORIZATION_BASE).authorizationRequestRepository(requestRepository))
                .redirectionEndpoint(r -> r.baseUri(CALLBACK_BASE + "/*"))
                .successHandler(handlers)
                .failureHandler(handlers));
    }
}
