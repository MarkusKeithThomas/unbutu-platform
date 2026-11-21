# Task – catalog (sự kiện, hạng vé)

## Đang mở
- [ ] **ORD-1 – Giữ vé 10′ (PR kế tiếp)**: inventory-service + **Redis (Lua atomic, chống oversell)** – `GET /api/inventory/events/{id}/availability` (A8b, "Còn x / y"), `POST /internal/reservations`; booking-service `POST /api/bookings` (JWT + `Idempotency-Key`, ≤ 4 vé) → `PENDING` + `expiresAt`, `GET /api/bookings/{id}`, `DELETE` huỷ, hết hạn tự nhả. UI: nút "Tiếp tục thanh toán" đọc giỏ `sessionStorage cart:<eventId>`. Thiết kế: private-docs/thiet-ke-ban-ve/02 §1, §3, §6. Còn phải chốt: catalog → inventory/booking đồng bộ tổng số vé + giá bằng HTTP nội bộ hay broker + Outbox.
- [ ] **CAT-1 – Cấp quyền ADMIN trên prod**: chạy `UPDATE users SET roles='CUSTOMER,ADMIN' WHERE email='…'` trong `identity_db` (prod) cho tài khoản quản trị, đăng nhập lại → vào `/admin/ticket-types`.
- [ ] **CAT-2 – Cột "Đã bán / Tổng"** trên màn hạng vé (design): cần inventory-service (tồn kho theo hạng vé). Khi có: chặn giảm tổng số vé xuống dưới số đã bán.
- [ ] **CAT-3 – Phát event** `ticket-type.upserted` / `event.published` qua Outbox (inventory khởi tạo tồn kho, booking giữ bảng giá).
- [ ] **CAT-4 – Chống ghi đè đồng thời** (2 admin cùng sửa): `@Version` + gửi `version` trong PUT → 409.
- [ ] **CAT-5 – Test** cho API admin (401/403/CRUD) khi bắt đầu viết test.

## Đã xong
- 2026-11-21: API + UI khách xem sự kiện / chọn vé (cache CDN 30 s, Slice, mô tả hạng vé).
- 2026-11-21: CRUD sự kiện + hạng vé (API admin, catalog verify JWT, màn `/admin/events`, `/admin/ticket-types`).
