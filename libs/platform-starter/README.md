# platform-starter

Spring Boot starter dùng chung cho **mọi service** (trừ phần WebFlux riêng của gateway), để quy ước được áp dụng đồng nhất thay vì copy-paste:

- Filter đọc/sinh `X-Request-Id` → MDC + baggage, trả lại trong response header.
- Cấu hình log JSON ECS (`service.name`, `service.environment`, `service.version`).
- Xử lý lỗi theo RFC 9457 Problem Details, luôn kèm `requestId`.
- `RestClient.Builder` cấu hình sẵn timeout + propagate trace header.
- Tiện ích Outbox / idempotent consumer (dùng chung cho booking, payment, notification).
