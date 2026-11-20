-- Người dùng: đăng nhập bằng email + mật khẩu và/hoặc tài khoản ngoài (Google).
CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,          -- luôn lưu chữ thường (chuẩn hoá ở code)
    password_hash  VARCHAR(100),                   -- NULL: chỉ đăng nhập bằng Google
    full_name      VARCHAR(200),
    phone          VARCHAR(30),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL,          -- ACTIVE | DISABLED (PENDING_VERIFY: sau, khi có notification)
    roles          VARCHAR(200) NOT NULL,          -- vd. "CUSTOMER" hoặc "CUSTOMER,ADMIN"
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Liên kết tài khoản ngoài: (provider, subject) là định danh bất biến bên Google ("sub"), không dùng email.
CREATE TABLE user_identities (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    provider   VARCHAR(20)  NOT NULL,              -- GOOGLE
    subject    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, subject)
);
CREATE INDEX ix_user_identities_user ON user_identities (user_id);

-- Refresh token: chỉ lưu SHA-256 (token gốc chỉ nằm trong cookie của trình duyệt).
-- Xoay vòng: mỗi lần dùng → thu hồi token cũ, cấp token mới cùng family_id.
-- Dùng lại token đã thu hồi = dấu hiệu bị đánh cắp → thu hồi cả family.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    family_id  UUID        NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
