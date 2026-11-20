package com.unbutu.ticket.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public enum Status { ACTIVE, DISABLED }

    public static final String ROLE_CUSTOMER = "CUSTOMER";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(String email, String passwordHash, String fullName, String phone, boolean emailVerified, Instant now) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.emailVerified = emailVerified;
        this.status = Status.ACTIVE;
        this.roles = ROLE_CUSTOMER;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Đăng ký bằng email + mật khẩu. Email chưa xác minh (xác minh làm sau, khi có notification-service). */
    public static User withPassword(String email, String passwordHash, String fullName, String phone, Instant now) {
        return new User(email, passwordHash, fullName, phone, false, now);
    }

    /** Tạo lần đầu từ Google: email đã được Google xác minh, không có mật khẩu. */
    public static User fromGoogle(String email, String fullName, Instant now) {
        return new User(email, null, fullName, null, true, now);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Gắn đăng nhập Google vào tài khoản có cùng email (Google đã xác minh email).
     * Nếu email của tài khoản này CHƯA được xác minh, mật khẩu hiện có có thể do người khác đặt trước
     * (chiếm tài khoản trước – "pre-hijacking") → xoá mật khẩu; chủ thật đăng nhập bằng Google.
     *
     * @return true nếu mật khẩu cũ đã bị xoá (caller phải thu hồi mọi phiên đang có)
     */
    public boolean confirmEmailViaGoogle(Instant now) {
        boolean dropPassword = !emailVerified && passwordHash != null;
        if (dropPassword) {
            passwordHash = null;
        }
        emailVerified = true;
        updatedAt = now;
        return dropPassword;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public List<String> roleList() {
        return Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }
}
