# platform-starter

Spring Boot starter dùng chung cho **service Spring MVC (servlet)**. Gateway (WebFlux) có filter riêng. Service chỉ cần
`implementation(project(":libs:platform-starter"))`, auto-configuration tự bật (`PlatformAutoConfiguration`).

**Đã có**
- `RequestIdFilter`: đọc `X-Request-Id` (gateway sinh) → MDC `requestId` + response header. Thiếu hoặc không hợp lệ thì sinh mới.
- `AccessLogFilter`: 1 dòng/request (`http.request.method`, `url.path`, `http.response.status_code`, `event.duration_ms`),
  cùng tên trường với access log của gateway. Bỏ qua `/actuator/**`.
- `RestClientCustomizer`: `RestClient.Builder` được inject tự gửi kèm `X-Request-Id`. `traceparent` do OpenTelemetry Java agent lo.

`trace_id`/`span_id` trong log do OpenTelemetry Java agent gắn vào MDC (chỉ bật ở prod, ADR-0006).
Log JSON ECS bật bằng `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (`deploy/compose.app.yml`).

**Dự kiến**
- Xử lý lỗi theo RFC 9457 Problem Details, luôn kèm `requestId`.
- Tiện ích Outbox / idempotent consumer (dùng cho booking, payment, notification).
