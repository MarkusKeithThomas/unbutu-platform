# Task – catalog (sự kiện, hạng vé)

## Đang mở
- [ ] **ROLLOUT PAY-1**: (1) ✅ secret `unbutu/prod/payment-service` (DB_PASSWORD, PAYMENT_HASH_SECRET); (2) merge + apply pro-ticket-workload **#6**; (3) Claude tạo DB `payment` (runbook); (4) merge PR PAY-1 → Approve deploy → Claude chạy thử 1 đơn thật trên prod.
- [ ] **PAY-1b – sau cổng giả lập**: (1) job **đối soát** payment `PENDING` > 15′ (giả lập `querydr`) → phát kết quả như IPN – cần cho `ipn-drop-rate`; (2) Outbox `booking.*` (`booking.confirmed`) cho ticketing/notification; (3) **adapter VNPay sandbox** (đăng ký merchant test, `PAYMENT_MOCK_ENABLED=false` trên prod); (4) cân nhắc chặn cổng giả lập trên prod (ai cũng "trả tiền" được) – chỉ bật cho tài khoản test hoặc tắt khi có sandbox.
- [ ] **ORD-1 – Giữ vé 10′ (PR kế tiếp)**: inventory-service + **Redis (Lua atomic, chống oversell)** – `GET /api/inventory/events/{id}/availability` (A8b, "Còn x / y"), `POST /internal/reservations`; booking-service `POST /api/bookings` (JWT + `Idempotency-Key`, ≤ 4 vé) → `PENDING` + `expiresAt`, `GET /api/bookings/{id}`, `DELETE` huỷ, hết hạn tự nhả. UI: nút "Tiếp tục thanh toán" đọc giỏ `sessionStorage cart:<eventId>`. Thiết kế: private-docs/thiet-ke-ban-ve/02 §1, §3, §6. **Đã chốt 2026-11-21**: (1) catalog → inventory/booking qua **Redpanda + Outbox** (`catalog.event.v1`: `ticket-type.upserted` giá + tổng số); (2) **Redis Lua giữ chỗ, Postgres là sổ cái**; (3) **4 vé / tài khoản / sự kiện**; (4) có UI bước 2 (form người mua + đếm ngược 10′ + "← Chọn lại vé" = huỷ giữ); nút "Xác nhận thanh toán" để PR thanh toán.
  Tách: ~~ORD-1a~~ → ~~ORD-1b~~ → ~~ORD-1c~~ (xong code 2026-11-21, chờ rollout). Mô tả ORD-1c gốc: `POST /api/bookings` (JWT, `Idempotency-Key`, 4 vé/tài khoản/sự kiện, giá từ bảng sao chép `catalog.event.v1`) → gọi inventory `POST /internal/events/{e}/reservations` (reservationId = bookingId) → `PENDING` + `expiresAt`; `GET /api/bookings/{id}`, `GET /api/bookings/me`, `DELETE` (huỷ → nhả vé), hết hạn → `EXPIRED`; UI bước 2 (form người mua + đếm ngược + "← Chọn lại vé").
  **Rollout prod ORD-1b** (theo thứ tự): (1) người dùng tạo secret `unbutu/prod/inventory-service` key `DB_PASSWORD`; (2) merge + apply pro-ticket-workload PR #5; (3) Claude chạy `docs/runbooks/add-service-database.md`; (4) merge PR inventory → Approve deploy.
- [ ] **CAT-2 – Cột "Đã bán / Tổng"** trên màn hạng vé (design): cần inventory-service (tồn kho theo hạng vé). Khi có: chặn giảm tổng số vé xuống dưới số đã bán.
- [ ] **CAT-4 – Chống ghi đè đồng thời** (2 admin cùng sửa): `@Version` + gửi `version` trong PUT → 409.
- [ ] **CAT-5 – Test** cho API admin (401/403/CRUD) khi bắt đầu viết test.

## Đã xong
- 2026-11-21: PAY-1 – payment-service (cổng giả lập giao thức VNPay, IPN + Outbox, hoàn tiền), libs/outbox (catalog chuyển sang), inventory chốt vé (sổ cái có điều kiện, idempotent), booking checkout + quyết toán + ân hạn 5′, UI xác nhận thanh toán + bước 3. Đo local: thành công / thất bại → trả lại / IPN giả mạo 97 / trả muộn còn vé → CONFIRMED / trả muộn hết vé → REFUNDED, sold = total.
- 2026-11-21: ROLLOUT ORD-1b + ORD-1c (#33 + ptw#5, DB inventory/booking trên prod).
- 2026-11-21: ORD-1c – booking-service (A9/A11/A12/A13, idempotency, 4 vé/tài khoản/sự kiện có khoá, hết hạn SKIP LOCKED) + UI bước 2 `/checkout/:id`. Đo local: song song 2 request vượt giới hạn → đúng 1 được; hết vé → 409 + CANCELLED; hết hạn → EXPIRED + trả vé.
- 2026-11-21: ORD-1b – inventory-service (Redis Lua + sổ cái Postgres, consumer catalog.event.v1, A8b, reservations, sweeper) + UI "Còn x / y". Đo local: 100 request song song / 10 vé → đúng 10 thành công.
- 2026-11-21: ORD-1a / CAT-3 – Redpanda (compose local + prod) + Transactional Outbox ở catalog → `catalog.event.v1` (`event.upserted|deleted`, `ticket-type.upserted|deleted`), backfill dữ liệu cũ (V4), ADR-0007.
- 2026-11-21: CAT-1 – cấp ADMIN prod cho `hthuongtayninh@gmail.com`.
- 2026-11-21: API + UI khách xem sự kiện / chọn vé (cache CDN 30 s, Slice, mô tả hạng vé).
- 2026-11-21: CRUD sự kiện + hạng vé (API admin, catalog verify JWT, màn `/admin/events`, `/admin/ticket-types`).
