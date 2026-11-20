package com.unbutu.ticket.identity.google;

import com.unbutu.ticket.identity.auth.AuthException;
import com.unbutu.ticket.identity.auth.RefreshCookies;
import com.unbutu.ticket.identity.token.RefreshTokenService;
import com.unbutu.ticket.identity.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Sau khi Google gọi lại và Spring đã verify ID token: tạo phiên của mình (refresh cookie) rồi đưa trình duyệt
 * về UI. Access token KHÔNG nằm trên URL – trang /auth/callback của UI gọi POST /api/identity/refresh để lấy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class GoogleLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final GoogleLoginService googleLogin;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;
    private final UiProperties ui;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidc = (OidcUser) authentication.getPrincipal();
        try {
            User user = googleLogin.login(new GoogleLoginService.GoogleProfile(oidc.getSubject(), oidc.getEmail(),
                    Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName()));
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(refreshTokens.issue(user.getId())).toString());
            response.sendRedirect(ui.baseUrl() + "/auth/callback");
        } catch (AuthException e) {
            response.sendRedirect(ui.baseUrl() + "/login?error=" + e.getCode());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Login Google thất bại: {}", exception.getMessage());
        response.sendRedirect(ui.baseUrl() + "/login?error=GOOGLE_LOGIN_FAILED");
    }
}
