package com.unbutu.ticket.identity.auth;

import com.unbutu.ticket.identity.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class MeController {

    private final UserRepository users;

    /** Hồ sơ người đang đăng nhập (UI điền sẵn form thanh toán). Cần header Authorization: Bearer <accessToken>. */
    @GetMapping("/api/identity/me")
    AuthDtos.UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .map(AuthDtos.UserResponse::from)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Tài khoản không còn tồn tại"));
    }
}
