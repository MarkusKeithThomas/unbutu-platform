package com.unbutu.ticket.identity.google;

import com.unbutu.ticket.identity.auth.AuthException;
import com.unbutu.ticket.identity.token.RefreshTokenService;
import com.unbutu.ticket.identity.user.User;
import com.unbutu.ticket.identity.user.UserIdentity;
import com.unbutu.ticket.identity.user.UserIdentityRepository;
import com.unbutu.ticket.identity.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class GoogleLoginService {

    /** Thông tin lấy từ ID token Google (đã được Spring verify chữ ký, aud, exp, nonce). */
    record GoogleProfile(String subject, String email, boolean emailVerified, String fullName) {
    }

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    /**
     * Thứ tự khớp tài khoản:
     * 1. đã liên kết (GOOGLE, sub) → user đó;
     * 2. chưa liên kết: chỉ khi Google xác nhận email → gắn vào user cùng email, hoặc tạo user mới.
     */
    @Transactional
    User login(GoogleProfile g) {
        Instant now = clock.instant();
        User user = identities.findByProviderAndSubject(UserIdentity.Provider.GOOGLE, g.subject())
                .flatMap(link -> users.findById(link.getUserId()))
                .orElseGet(() -> linkOrCreate(g, now));
        if (!user.isActive()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị khoá");
        }
        return user;
    }

    private User linkOrCreate(GoogleProfile g, Instant now) {
        if (!g.emailVerified() || g.email() == null) {
            throw new AuthException(HttpStatus.FORBIDDEN, "GOOGLE_EMAIL_NOT_VERIFIED", "Email Google chưa được xác minh");
        }
        User user = users.findByEmail(User.normalizeEmail(g.email())).map(existing -> {
            if (existing.confirmEmailViaGoogle(now)) {
                // Mật khẩu cũ đặt trên email chưa xác minh → có thể không phải của chủ email: xoá + đá mọi phiên.
                refreshTokens.revokeAllOfUser(existing.getId());
                log.warn("User {}: gắn Google vào tài khoản email chưa xác minh → xoá mật khẩu cũ, thu hồi mọi phiên",
                        existing.getId());
            }
            return existing;
        }).orElseGet(() -> users.save(User.fromGoogle(g.email(), g.fullName(), now)));
        identities.save(new UserIdentity(user.getId(), UserIdentity.Provider.GOOGLE, g.subject(), now));
        return user;
    }
}
