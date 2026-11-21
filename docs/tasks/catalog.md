# Task – catalog (sự kiện, hạng vé)

## Đang mở
- [ ] **ROLLOUT PAY-1**: (1) ✅ secret `unbutu/prod/payment-service` (DB_PASSWORD, PAYMENT_HASH_SECRET); (2) merge + apply pro-ticket-workload **#6**; (3) Claude tạo DB `payment` (runbook); (4) merge PR PAY-1 → Approve deploy → Claude chạy thử 1 đơn thật trên prod.
- [ ] **PAY-1c (3) – chặn cổng giả lập trên prod**: hiện ai cũng "trả tiền" được – chỉ bật cho tài khoản test/ADMIN. **Hoãn tới sau load test** (load test cần trả tiền giả lập hàng loạt). VNPay sandbox: KHÔNG làm.
- [ ] **ORD-1 – Giữ vé 10′ (PR kế tiếp)**: inventory-service + **Redis (Lua atomic, chống oversell)** – `GET /api/inventory/events/{id}/availability` (A8b, "Còn x / y"), `POST /internal/reservations`; booking-service `POST /api/bookings` (JWT + `Idempotency-Key`, ≤ 4 vé) → `PENDING` + `expiresAt`, `GET /api/bookings/{id}`, `DELETE` huỷ, hết hạn tự nhả. UI: nút "Tiếp tục thanh toán" đọc giỏ `sessionStorage cart:<eventId>`. Thiết kế: private-docs/thiet-ke-ban-ve/02 §1, §3, §6. **Đã chốt 2026-11-21**: (1) catalog → inventory/booking qua **Redpanda + Outbox** (`catalog.event.v1`: `ticket-type.upserted` giá + tổng số); (2) **Redis Lua giữ chỗ, Postgres là sổ cái**; (3) **4 vé / tài khoản / sự kiện**; (4) có UI bước 2 (form người mua + đếm ngược 10′ + "← Chọn lại vé" = huỷ giữ); nút "Xác nhận thanh toán" để PR thanh toán.
  Tách: ~~ORD-1a~~ → ~~ORD-1b~~ → ~~ORD-1c~~ (xong code 2026-11-21, chờ rollout). Mô tả ORD-1c gốc: `POST /api/bookings` (JWT, `Idempotency-Key`, 4 vé/tài khoản/sự kiện, giá từ bảng sao chép `catalog.event.v1`) → gọi inventory `POST /internal/events/{e}/reservations` (reservationId = bookingId) → `PENDING` + `expiresAt`; `GET /api/bookings/{id}`, `GET /api/bookings/me`, `DELETE` (huỷ → nhả vé), hết hạn → `EXPIRED`; UI bước 2 (form người mua + đếm ngược + "← Chọn lại vé").
  **Rollout prod ORD-1b** (theo thứ tự): (1) người dùng tạo secret `unbutu/prod/inventory-service` key `DB_PASSWORD`; (2) merge + apply pro-ticket-workload PR #5; (3) Claude chạy `docs/runbooks/add-service-database.md`; (4) merge PR inventory → Approve deploy.
- [ ] **CAT-2 – Cột "Đã bán / Tổng"** trên màn hạng vé (design): cần inventory-service (tồn kho theo hạng vé). Khi có: chặn giảm tổng số vé xuống dưới số đã bán.
- [ ] **CAT-4 – Chống ghi đè đồng thời** (2 admin cùng sửa): `@Version` + gửi `version` trong PUT → 409.
- [ ] **CAT-5 – Test** cho API admin (401/403/CRUD) khi bắt đầu viết test.

## Đã xong
- 2026-11-21: Static membership cho consumer inventory (`inventory-1-catalog`); identity local: `kid` theo thumbprint key tạm → restart identity không còn làm service khác 401 (prod: key cố định từ secret, không bị).
- 2026-11-21: PAY-1c (1)+(2) (#36) – Outbox `booking.event.v1` (`booking.confirmed`, event béo cho ticketing/notification), static membership consumer booking (restart không rebalance, generation giữ nguyên).
- 2026-11-21: PAY-1b – ngân hàng giả lập có trạng thái (sổ GD, hàng đợi IPN bền + retry, querydr VNPay), 8 lỗi giả lập bật bằng env (`IpnFault` cắm thêm), job đối soát 1′ / treo > 2′ + `reconciliation_log`, mở lại GD bị kết luận bỏ ngang khi tiền thật sự về. Runbook `docs/runbooks/payment-drills.md`. Đã luyện đủ 8 lỗi + bỏ ngang ở local.
- 2026-11-21: PAY-1 – payment-service (cổng giả lập giao thức VNPay, IPN + Outbox, hoàn tiền), libs/outbox (catalog chuyển sang), inventory chốt vé (sổ cái có điều kiện, idempotent), booking checkout + quyết toán + ân hạn 5′, UI xác nhận thanh toán + bước 3. Đo local: thành công / thất bại → trả lại / IPN giả mạo 97 / trả muộn còn vé → CONFIRMED / trả muộn hết vé → REFUNDED, sold = total.
- 2026-11-21: ROLLOUT ORD-1b + ORD-1c (#33 + ptw#5, DB inventory/booking trên prod).
- 2026-11-21: ORD-1c – booking-service (A9/A11/A12/A13, idempotency, 4 vé/tài khoản/sự kiện có khoá, hết hạn SKIP LOCKED) + UI bước 2 `/checkout/:id`. Đo local: song song 2 request vượt giới hạn → đúng 1 được; hết vé → 409 + CANCELLED; hết hạn → EXPIRED + trả vé.
- 2026-11-21: ORD-1b – inventory-service (Redis Lua + sổ cái Postgres, consumer catalog.event.v1, A8b, reservations, sweeper) + UI "Còn x / y". Đo local: 100 request song song / 10 vé → đúng 10 thành công.
- 2026-11-21: ORD-1a / CAT-3 – Redpanda (compose local + prod) + Transactional Outbox ở catalog → `catalog.event.v1` (`event.upserted|deleted`, `ticket-type.upserted|deleted`), backfill dữ liệu cũ (V4), ADR-0007.
- 2026-11-21: CAT-1 – cấp ADMIN prod cho `hthuongtayninh@gmail.com`.
- 2026-11-21: API + UI khách xem sự kiện / chọn vé (cache CDN 30 s, Slice, mô tả hạng vé).
- 2026-11-21: CRUD sự kiện + hạng vé (API admin, catalog verify JWT, màn `/admin/events`, `/admin/ticket-types`).
