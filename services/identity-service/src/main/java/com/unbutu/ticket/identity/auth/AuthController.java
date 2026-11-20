package com.unbutu.ticket.identity.auth;

import com.unbutu.ticket.identity.token.AccessTokenService;
import com.unbutu.ticket.identity.token.InvalidRefreshTokenException;
import com.unbutu.ticket.identity.token.RefreshTokenService;
import com.unbutu.ticket.identity.user.User;
import com.unbutu.ticket.identity.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
class AuthController {

    private final AuthService auth;
    private final UserRepository users;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    /** Đăng ký xong là đăng nhập luôn (email chưa xác minh – làm sau). */
    @PostMapping("/register")
    ResponseEntity<AuthDtos.TokenResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        User user = auth.register(req);
        return session(user, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return session(auth.authenticate(req.email(), req.password()), HttpStatus.OK);
    }

    /** Đổi refresh token (cookie) lấy access token mới; refresh token được xoay vòng. */
    @PostMapping("/refresh")
    ResponseEntity<AuthDtos.TokenResponse> refresh(@CookieValue(name = "${identity.refresh.cookie-name}", required = false) String token) {
        if (token == null || token.isBlank()) {
            throw AuthException.invalidRefresh();
        }
        try {
            RefreshTokenService.Rotation rotation = refreshTokens.rotate(token);
            User user = users.findById(rotation.userId()).filter(User::isActive)
                    .orElseThrow(AuthException::invalidRefresh);
            return tokenResponse(user, rotation.newToken(), HttpStatus.OK);
        } catch (InvalidRefreshTokenException e) {
            throw AuthException.invalidRefresh();
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = "${identity.refresh.cookie-name}", required = false) String token) {
        if (token != null && !token.isBlank()) {
            refreshTokens.revoke(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    private ResponseEntity<AuthDtos.TokenResponse> session(User user, HttpStatus status) {
        return tokenResponse(user, refreshTokens.issue(user.getId()), status);
    }

    private ResponseEntity<AuthDtos.TokenResponse> tokenResponse(User user, String refreshToken, HttpStatus status) {
        AccessTokenService.AccessToken access = accessTokens.issue(user);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(refreshToken).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.TokenResponse(access.value(), "Bearer", access.expiresInSeconds(),
                        AuthDtos.UserResponse.from(user)));
    }
}
