-- Transactional Outbox: thay đổi nghiệp vụ + event ghi CÙNG 1 transaction → không bao giờ "lưu DB xong mà mất event"
-- (hoặc ngược lại). OutboxRelay đọc dòng chưa gửi theo thứ tự id → Redpanda → đánh dấu published_at.
CREATE TABLE outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,      -- EventEnvelope.eventId (consumer dedupe)
    topic        VARCHAR(100) NOT NULL,
    msg_key      VARCHAR(100) NOT NULL,             -- Kafka key (id sự kiện) → cùng partition, đúng thứ tự
    type         VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,             -- toàn bộ EventEnvelope dạng JSON
    occurred_at  TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

-- Relay chỉ quét phần chưa gửi (nhỏ) – không quét lịch sử.
CREATE INDEX ix_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

-- Backfill: dữ liệu tạo TRƯỚC khi có outbox (prod đã có) → phát snapshot để inventory/booking dựng được trạng thái đầu.
-- Thời gian ghi ISO-8601 UTC ("...Z") giống Jackson serialize Instant.
INSERT INTO outbox (event_id, topic, msg_key, type, payload, occurred_at)
SELECT e.mid, 'catalog.event.v1', e.id::text, 'event.upserted',
       jsonb_build_object(
           'eventId', e.mid, 'type', 'event.upserted',
           'occurredAt', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
           'payload', jsonb_build_object(
               'eventId', e.id, 'title', e.title, 'type', e.type, 'venue', e.venue,
               'startsAt', to_char(e.starts_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
               'updatedAt', to_char(e.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))),
       now()
FROM (SELECT ev.*, gen_random_uuid() AS mid FROM events ev) e   -- volatile trong select-list: 1 id mới / dòng
ORDER BY e.created_at;

INSERT INTO outbox (event_id, topic, msg_key, type, payload, occurred_at)
SELECT t.mid, 'catalog.event.v1', t.event_id::text, 'ticket-type.upserted',
       jsonb_build_object(
           'eventId', t.mid, 'type', 'ticket-type.upserted',
           'occurredAt', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
           'payload', jsonb_build_object(
               'ticketTypeId', t.id, 'eventId', t.event_id, 'name', t.name, 'price', t.price,
               'totalQuantity', t.total_quantity, 'status', t.status,
               'updatedAt', to_char(t.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))),
       now()
FROM (SELECT tt.*, gen_random_uuid() AS mid FROM ticket_types tt) t
ORDER BY t.created_at;
