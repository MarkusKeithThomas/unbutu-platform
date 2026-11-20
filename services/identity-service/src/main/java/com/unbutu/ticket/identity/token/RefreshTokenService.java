package com.unbutu.ticket.identity.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh token dạng chuỗi ngẫu nhiên (không phải JWT), lưu SHA-256 trong DB.
 * Mỗi lần dùng: thu hồi token cũ + cấp token mới cùng "family". Token đã thu hồi bị dùng lại
 * → coi như bị đánh cắp → thu hồi cả family (cả kẻ trộm lẫn chủ thật phải đăng nhập lại).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    /** Kết quả xoay vòng: user nào + token mới (giá trị gốc, chỉ để đặt vào cookie). */
    public record Rotation(UUID userId, String newToken, boolean persistent) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final RefreshProperties props;
    private final Clock clock;

    /** Phiên đăng nhập mới (family mới). {@code persistent}: "Ghi nhớ đăng nhập" – giữ nguyên qua các lần xoay vòng. */
    @Transactional
    public String issue(UUID userId, boolean persistent) {
        return issueInFamily(userId, UUID.randomUUID(), persistent, clock.instant());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown refresh token"));
        if (current.isRevoked()) {
            int n = tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token đã thu hồi bị dùng lại → thu hồi family {} ({} token) của user {}",
                    current.getFamilyId(), n, current.getUserId());
            throw new InvalidRefreshTokenException("refresh token reused");
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw new InvalidRefreshTokenException("refresh token expired");
        }
        current.revoke(now);
        String next = issueInFamily(current.getUserId(), current.getFamilyId(), current.isPersistent(), now);
        return new Rotation(current.getUserId(), next, current.isPersistent());
    }

    /** Đăng xuất phiên hiện tại (cả family). Token lạ/hết hạn: bỏ qua – logout luôn thành công. */
    @Transactional
    public void revoke(String rawToken) {
        Instant now = clock.instant();
        tokens.findByTokenHash(hash(rawToken)).ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), now));
    }

    @Transactional
    public void revokeAllOfUser(UUID userId) {
        tokens.revokeAllOfUser(userId, clock.instant());
    }

    private String issueInFamily(UUID userId, UUID familyId, boolean persistent, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(userId, familyId, hash(raw), persistent, now.plus(props.ttl()), now));
        return raw;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
