-- Bảng giá sao chép từ catalog.event.v1 – booking tính tiền KHÔNG gọi catalog (catalog sập vẫn bán được).
CREATE TABLE ticket_type_prices (
    ticket_type_id     UUID         PRIMARY KEY,
    event_id           UUID         NOT NULL,
    name               VARCHAR(100) NOT NULL,
    price              BIGINT       NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    deleted            BOOLEAN      NOT NULL DEFAULT false,
    catalog_updated_at TIMESTAMPTZ  NOT NULL      -- version snapshot: chỉ nhận bản mới hơn
);

CREATE TABLE event_info (                         -- tiêu đề/giờ/địa điểm để hiển thị trên đơn
    event_id           UUID         PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    venue              VARCHAR(255),
    starts_at          TIMESTAMPTZ,
    catalog_updated_at TIMESTAMPTZ  NOT NULL
);

CREATE SEQUENCE booking_code_seq START 100000;

CREATE TABLE bookings (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(16)  NOT NULL UNIQUE,     -- ORD-100000 (hiển thị cho khách)
    user_id         UUID         NOT NULL,            -- sub của access token
    event_id        UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL,            -- PENDING | CANCELLED | EXPIRED (PAY-1: AWAITING_PAYMENT, CONFIRMED…)
    cancel_reason   VARCHAR(32),                      -- USER | SOLD_OUT | NOT_ON_SALE | INVENTORY_ERROR
    ticket_count    INTEGER      NOT NULL CHECK (ticket_count > 0),
    subtotal        BIGINT       NOT NULL,
    fee             BIGINT       NOT NULL,
    total           BIGINT       NOT NULL,
    buyer_name      VARCHAR(100),                     -- PAY-1: điền ở bước thanh toán
    buyer_email     VARCHAR(255),
    buyer_phone     VARCHAR(20),
    idempotency_key VARCHAR(64)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,            -- cùng key nhưng khác nội dung → 409 IDEMPOTENCY_CONFLICT
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_bookings_idempotency UNIQUE (user_id, idempotency_key)
);

-- "Đơn của tôi" (mới trước) + đếm giới hạn 4 vé / tài khoản / sự kiện.
CREATE INDEX ix_bookings_user_created ON bookings (user_id, created_at DESC);
CREATE INDEX ix_bookings_user_event ON bookings (user_id, event_id);
-- Job hết hạn chỉ quét đơn đang giữ (nhỏ), không quét lịch sử.
CREATE INDEX ix_bookings_pending_expiry ON bookings (expires_at) WHERE status = 'PENDING';

CREATE TABLE booking_items (
    booking_id     UUID         NOT NULL REFERENCES bookings (id),
    ticket_type_id UUID         NOT NULL,
    name           VARCHAR(100) NOT NULL,            -- chụp lại lúc đặt (catalog đổi tên/giá sau không ảnh hưởng đơn)
    unit_price     BIGINT       NOT NULL,
    quantity       INTEGER      NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (booking_id, ticket_type_id)
);
