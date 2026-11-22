# Task – admin dashboard (vận hành bán vé)

Mục tiêu: admin mở `/admin/dashboard` thấy ngay tình hình bán vé – vé đã bán, đơn đang chờ, đơn lỗi, doanh thu – và
tra cứu được từng đơn/thanh toán để xử lý khiếu nại. Hiện admin chỉ có `/admin/events`, `/admin/ticket-types`, `/admin/loadtest`.

Dữ liệu đang có (mỗi service 1 DB, không JOIN chéo):
- `booking_db.bookings`: `status` (PENDING · AWAITING_PAYMENT · CONFIRMED · CANCELLED · EXPIRED · REFUND_PENDING · REFUNDED),
  `total` (VND), `event_id`, `created_at`; `event_info` (bản sao từ catalog) → gộp theo sự kiện không cần gọi catalog.
- `payment_db.payments`: `status` (PENDING · SUCCEEDED · FAILED · REFUNDED), `amount`, `gateway_status` (mã VNPay).
- `notification_db.tickets`: `status` (VALID · USED · VOID).

## Cần chốt trước khi code (hỏi người dùng)
1. **Nguồn số liệu bản 1**: (A, đề xuất) API admin đọc thẳng DB từng service (booking + payment), UI ghép lại –
   nhanh, đủ cho quy mô hiện tại; hay (B) `reporting-service` nghe `booking.event.v1` dựng bảng tổng hợp riêng (CQRS) –
   đúng hướng 50k rps hơn nhưng cần thêm event cho mọi trạng thái (hiện outbox mới có `booking.confirmed`).
2. **Doanh thu tính gì**: tổng `total` của đơn CONFIRMED (gộp) hay trừ hoàn tiền (thuần)? Theo ngày giờ VN (UTC+7)?
3. **Sự kiện ẩn / load test**: mặc định loại khỏi số liệu (có công tắc "Gồm dữ liệu load test")?

## Đang mở
- [ ] **ADM-1 · API thống kê** (booking-service, role ADMIN): `GET /api/bookings/admin/stats?from=&to=&eventId=` →
  số đơn theo trạng thái, số vé đã bán, doanh thu, tỷ lệ chuyển đổi (CONFIRMED / tổng đơn), doanh thu theo ngày + theo sự kiện.
  50k rps: không `COUNT(*)` toàn bảng mỗi lần tải – cache 30–60 s (Caffeine/Redis) hoặc bảng tổng hợp theo giờ cập nhật định kỳ;
  index `(status, created_at)`, `(event_id, status)`.
- [ ] **ADM-2 · Danh sách đơn** `GET /api/bookings/admin/bookings?status=&eventId=&q=<email|mã đơn>&cursor=` –
  phân trang keyset (`created_at, id`), không OFFSET; chi tiết đơn: dòng vé, lịch sử trạng thái, thanh toán, vé QR đã phát.
- [ ] **ADM-3 · Thanh toán lỗi / treo** (payment-service): `GET /api/payments/admin/payments?status=FAILED|PENDING` + mã lỗi
  `gateway_status`; đánh dấu PENDING quá hạn (IPN không về) để đối soát; số lần hoàn tiền + REFUND_PENDING bị kẹt.
- [ ] **ADM-4 · Trang `/admin/dashboard`** (frontend, `RequireAdmin`, đặt làm trang mặc định của `/admin`):
  - Hàng KPI: doanh thu hôm nay / 7 ngày, vé đã bán, đơn đang chờ (PENDING + AWAITING_PAYMENT), đơn lỗi/hết hạn, tỷ lệ chuyển đổi.
  - Biểu đồ doanh thu + số vé theo ngày (dùng lại `admin/LineChart.tsx`); bảng top sự kiện (đã bán / tổng – nối CAT-2).
  - Tab "Đơn hàng" (ADM-2) và "Thanh toán lỗi" (ADM-3) có lọc trạng thái, tìm theo email/mã đơn, bấm xem chi tiết.
  - Bộ lọc khoảng ngày + sự kiện; tự làm mới 30 s.
- [ ] **ADM-5 · Thao tác admin** (sau khi xem được): hủy đơn treo, hoàn tiền thủ công (gọi `/internal/payments/bookings/{id}/refund`),
  xuất CSV đơn hàng. Mỗi thao tác ghi audit log (ai, lúc nào, lý do).
- [ ] **ADM-6 · Người dùng** (identity-service): danh sách user, khóa/mở (`ACTIVE`/`DISABLED`), xem đơn của 1 user.

Phụ thuộc/liên quan: CAT-2 (đã bán/tổng theo hạng vé – inventory), TIX-2 (check-in → vé USED, hoàn tiền → VOID).
Kiểm tra: compile + curl API + xem trên trình duyệt (chưa viết test – theo quy ước hiện tại).

## Đã xong
