package com.unbutu.ticket.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tài khoản ngoài gắn với 1 user. Định danh = (provider, subject) – "sub" của Google, không đổi kể cả khi đổi email. */
@Entity
@Table(name = "user_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {

    public enum Provider { GOOGLE }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String subject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserIdentity(UUID userId, Provider provider, String subject, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.createdAt = now;
    }
}
