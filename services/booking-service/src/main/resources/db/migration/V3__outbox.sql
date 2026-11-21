-- PAY-1c: Transactional Outbox (libs/outbox) → booking.event.v1 (booking.confirmed cho ticketing / notification).
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
