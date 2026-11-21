# Task – catalog (sự kiện, hạng vé)

## Đang mở
- [ ] **CAT-1 – Cấp quyền ADMIN trên prod**: chạy `UPDATE users SET roles='CUSTOMER,ADMIN' WHERE email='…'` trong `identity_db` (prod) cho tài khoản quản trị, đăng nhập lại → vào `/admin/ticket-types`.
- [ ] **CAT-2 – Cột "Đã bán / Tổng"** trên màn hạng vé (design): cần inventory-service (tồn kho theo hạng vé). Khi có: chặn giảm tổng số vé xuống dưới số đã bán.
- [ ] **CAT-3 – Phát event** `ticket-type.upserted` / `event.published` qua Outbox (inventory khởi tạo tồn kho, booking giữ bảng giá).
- [ ] **CAT-4 – Chống ghi đè đồng thời** (2 admin cùng sửa): `@Version` + gửi `version` trong PUT → 409.
- [ ] **CAT-5 – Test** cho API admin (401/403/CRUD) khi bắt đầu viết test.

## Đã xong
- 2026-11-21: CRUD sự kiện + hạng vé (API admin, catalog verify JWT, màn `/admin/events`, `/admin/ticket-types`).
