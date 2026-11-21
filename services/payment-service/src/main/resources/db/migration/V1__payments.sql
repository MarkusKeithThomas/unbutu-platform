-- Mỗi lần khách bấm "Xác nhận thanh toán" cho 1 đơn = 1 attempt. Tối đa 1 attempt PENDING / đơn (bấm lại → cùng URL).
CREATE TABLE payments (
    id                     UUID        PRIMARY KEY,
    booking_id             UUID        NOT NULL,
    booking_code           VARCHAR(16) NOT NULL,
    attempt                INTEGER     NOT NULL,
    txn_ref                VARCHAR(40) NOT NULL UNIQUE,   -- vnp_TxnRef (ORD-100000-1)
    amount                 BIGINT      NOT NULL CHECK (amount > 0),   -- VND
    method                 VARCHAR(16) NOT NULL,          -- VNBANK | VNPAYQR | INTCARD
    status                 VARCHAR(16) NOT NULL,          -- PENDING | SUCCEEDED | FAILED | REFUNDED
    expire_at              TIMESTAMPTZ NOT NULL,          -- = hạn giữ vé (vnp_ExpireDate)
    provider_txn_no        VARCHAR(40),
    provider_response_code VARCHAR(8),
    paid_at                TIMESTAMPTZ,
    refunded_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_attempt UNIQUE (booking_id, attempt)
);

CREATE UNIQUE INDEX uq_payments_one_pending ON payments (booking_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_payments_one_succeeded ON payments (booking_id) WHERE status IN ('SUCCEEDED', 'REFUNDED');

-- Transactional Outbox (libs/outbox).
CREATE TABLE outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    topic        VARCHAR(100) NOT NULL,
    msg_key      VARCHAR(100) NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
