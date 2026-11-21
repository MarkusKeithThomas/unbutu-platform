# outbox

Transactional Outbox dùng chung (ADR-0007). Service thêm `implementation(project(":libs:outbox"))` + bảng `outbox`
(Flyway của service, copy nguyên từ `services/catalog-service/.../V4__outbox.sql` phần CREATE TABLE + INDEX).

- `Outbox.append(topic, key, type, payload)` – gọi **trong** transaction nghiệp vụ (MANDATORY), ghi `EventEnvelope` JSON.
- `OutboxRelay` – mỗi `outbox.poll-interval` (500 ms): `pg_try_advisory_xact_lock` theo `spring.application.name`
  (1 relay / service mỗi lúc), gửi lô `acks=all`, đánh dấu `published_at`; broker lỗi → backoff tới 30 s; dọn bản đã gửi > 7 ngày.
- Tắt relay (test không có Kafka): `outbox.relay-enabled=false`. Topic: khai báo `NewTopic` bean ở service – relay tạo lại trước lần gửi đầu.
