package com.unbutu.ticket.identity.auth;

import com.unbutu.ticket.identity.token.RefreshProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie chứa refresh token: HttpOnly (JS không đọc được → XSS không lấy được), Secure (prod),
 * SameSite=Lax (trình duyệt không gửi kèm POST từ site khác → chặn CSRF vào /refresh, /logout),
 * Path=/api/identity (không gửi kèm request tới service khác). UI trên Vercel rewrite /api → cùng origin.
 */
@Component
@RequiredArgsConstructor
public class RefreshCookies {

    private final RefreshProperties props;

    public String name() {
        return props.cookieName();
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(props.ttl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(props.cookieName(), value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Lax")
                .path(props.cookiePath());
    }
}
