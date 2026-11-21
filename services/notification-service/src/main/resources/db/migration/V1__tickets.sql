-- Vé điện tử: phát khi nhận booking.confirmed (booking.event.v1). Bất biến sau khi phát (trừ status khi check-in sau này).
-- Thông tin sự kiện/người mua chụp lại từ event → đọc vé không gọi service khác.
CREATE TABLE tickets (
    id               UUID         PRIMARY KEY,           -- UUID theo tên (bookingId + seq) → phát lại cho cùng id
    booking_id       UUID         NOT NULL,
    booking_code     VARCHAR(16)  NOT NULL,
    user_id          UUID         NOT NULL,              -- chủ vé (sub của access token)
    seq              INTEGER      NOT NULL,              -- vé thứ mấy trong đơn (1..N)
    event_id         UUID         NOT NULL,
    event_title      VARCHAR(255),
    venue            VARCHAR(255),
    starts_at        TIMESTAMPTZ,
    ticket_type_id   UUID         NOT NULL,
    ticket_type_name VARCHAR(100) NOT NULL,
    unit_price       BIGINT       NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'VALID',   -- VALID (sau: USED khi check-in, VOID khi hoàn tiền)
    qr               TEXT         NOT NULL,              -- JWS ES256 (kid trong header) – cổng soát vé verify offline qua JWKS
    issued_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tickets_booking_seq UNIQUE (booking_id, seq)    -- event lặp → ON CONFLICT DO NOTHING
);

-- "Vé của tôi" (mới trước). Vé của 1 đơn: dùng uq_tickets_booking_seq.
CREATE INDEX ix_tickets_user_issued ON tickets (user_id, issued_at DESC, seq);
