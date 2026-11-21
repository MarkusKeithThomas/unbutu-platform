# ADR-0009: Vé điện tử QR (ES256) phát trong notification-service

- Trạng thái: Chấp nhận · 2026-11-21
- Liên quan: ADR-0007 (Redpanda + Outbox), private-docs/thiet-ke-ban-ve (thiết kế đích có `ticketing-service` riêng)

## Bối cảnh
Sau khi đơn `CONFIRMED`, khách cần **thấy vé ngay** (QR) – gửi email chưa làm được ở tải cao (giới hạn SMTP/SES, retry, bounce)
nên để sau. Khách cũng cần **lịch sử mua vé** để mở lại vé bất cứ lúc nào.

Thiết kế đích tách `ticketing-service` (phát vé, check-in, chuyển nhượng) khỏi `notification-service` (email). Người dùng chọn
đặt vé QR vào **notification-service** ngay từ đầu: email vé (bước sau) cần chính dữ liệu vé + QR → cùng chỗ thì gửi mail
không phải gọi service khác.

## Quyết định
1. **notification-service** nghe `booking.event.v1` (`booking.confirmed`, event "béo" – #36) → phát N vé trong 1 transaction.
   Idempotent: id vé = UUID theo tên `bookingId:seq`, `UNIQUE (booking_id, seq)` + `ON CONFLICT DO NOTHING`.
   Static membership `notification-1-booking` như booking/inventory.
2. **QR = JWS compact ES256** `{tid, eid, tt, iat}` (header có `kid`). Cổng soát vé verify **offline** bằng JWKS công khai
   `GET /api/tickets/.well-known/jwks.json` – không làm giả/sửa được (đã kiểm: sửa hạng vé → chữ ký sai). Chống dùng 1 vé
   2 lần = check-in đánh dấu `USED` có điều kiện (làm sau).
   Khoá: EC P-256 PKCS#8 base64 (`openssl genpkey … -outform DER`) trong Secrets Manager `QR_SIGNING_KEY` (ADR-0004);
   local/test tự sinh key tạm, `kid` theo thumbprint.
3. **API** (JWT, chỉ chủ vé): `GET /api/tickets/bookings/{bookingId}` (rỗng = chưa phát → UI poll 1 s; có vé → bất biến,
   `Cache-Control: private, max-age=300`), `GET /api/tickets/me` (Slice). Gateway: `/api/tickets/**` → notification-service.
4. **Lịch sử đơn** dùng API có sẵn của booking `GET /api/bookings/me` (Slice) – UI `/orders`; đơn `CONFIRMED` → `/orders/{id}` hiện QR.
5. Package `com.unbutu.ticket.notification.ticket` **độc lập** phần gửi thông báo: không dùng chung bảng/bean với email.

## Hệ quả
- Tách `ticketing-service` sau này (khi tải soát vé ngày diễn ra sự kiện cần scale riêng, hoặc thêm chuyển nhượng):
  chuyển nguyên package + bảng `tickets` + giữ `kid`/khoá QR → vé đã phát vẫn hợp lệ; gateway đổi route `/api/tickets/**`.
- notification-service thành service **có trạng thái quan trọng** (vé là dữ liệu gốc khi soát vé) – không còn là
  "gửi rồi quên": cần backup `notification_db` như các DB khác.
- Đơn hoàn tiền sau khi đã phát vé (chưa xảy ra: hoàn tiền chỉ trước CONFIRMED) → khi có: `booking.refunded` → vé `VOID`.
- 50k rps: đọc vé theo index `(booking_id, seq)` / `(user_id, issued_at)`, cache trình duyệt 5′; phát vé là ghi bất đồng bộ
  theo partition (key = bookingId) – thêm instance = thêm consumer (mỗi bản một `KAFKA_GROUP_INSTANCE_ID`).
