# Task – admin dashboard (vận hành bán vé)

Mục tiêu: admin mở `/admin/dashboard` thấy ngay tình hình bán vé – vé đã bán, đơn đang chờ, đơn lỗi, doanh thu – và
tra cứu được từng đơn/thanh toán để xử lý khiếu nại. Hiện admin chỉ có `/admin/events`, `/admin/ticket-types`, `/admin/loadtest`.

Dữ liệu đang có (mỗi service 1 DB, không JOIN chéo):
- `booking_db.bookings`: `status` (PENDING · AWAITING_PAYMENT · CONFIRMED · CANCELLED · EXPIRED · REFUND_PENDING · REFUNDED),
  `total` (VND), `event_id`, `created_at`; `event_info` (bản sao từ catalog) → gộp theo sự kiện không cần gọi catalog.
- `payment_db.payments`: `status` (PENDING · SUCCEEDED · FAILED · REFUNDED), `amount`, `gateway_status` (mã VNPay).
- `notification_db.tickets`: `status` (VALID · USED · VOID).

## Đã chốt (2026-11-22)
1. **Nguồn số liệu – phương án A**: mỗi service mở API admin đọc DB của nó, UI ghép. Cache 60 s trong bộ nhớ, index
   `(created_at)` / `(event_id, created_at)`, keyset cho danh sách. Khi bảng lên hàng chục triệu dòng → bảng tổng hợp theo giờ
   hoặc `reporting-service` (CQRS) – giữ nguyên API.
2. **Doanh thu**: *thuần* = đơn CONFIRMED (KPI chính); *đã nhận* = CONFIRMED + REFUND_PENDING + REFUNDED; hoàn tiền hiện riêng.
   Gom theo **ngày tạo đơn, giờ VN** (`Asia/Ho_Chi_Minh`).
3. **Load test**: mặc định loại sự kiện ẩn (`event_info.hidden`, chép từ `catalog.event.v1`); công tắc "Gồm dữ liệu load test".

## Đang mở
- [x] **ADM-1 · API thống kê** (2026-11-22, `admin/AdminStatsService`; tỷ lệ chốt đơn = CONFIRMED / đơn đã có kết quả, bỏ đơn đang chờ) (booking-service, role ADMIN): `GET /api/bookings/admin/stats?from=&to=&eventId=` →
  số đơn theo trạng thái, số vé đã bán, doanh thu, tỷ lệ chuyển đổi (CONFIRMED / tổng đơn), doanh thu theo ngày + theo sự kiện.
  50k rps: không `COUNT(*)` toàn bảng mỗi lần tải – cache 30–60 s (Caffeine/Redis) hoặc bảng tổng hợp theo giờ cập nhật định kỳ;
  index `(status, created_at)`, `(event_id, status)`.
- [x] **ADM-2 · Danh sách đơn** (2026-11-22) `GET /api/bookings/admin/bookings` + `/{id}` – keyset `(created_at, id)`, tìm chính xác
  mã đơn / email / SĐT / id đơn / id tài khoản; chi tiết: dòng vé, mốc thời gian, mọi lần thanh toán. Trang `/admin/orders`.
  *Còn*: vé QR đã phát trong chi tiết (cần `GET /api/tickets/admin/bookings/{id}` + map role ở notification-service);
  lịch sử trạng thái (chưa có bảng lịch sử – hiện chỉ các mốc created/confirmed/updated).
- [x] **ADM-3 · Thanh toán lỗi / treo** (2026-11-22) – payment-service `/internal/admin/payments[/summary]`, booking-service kiểm ADMIN
  rồi chuyển tiếp (`/api/bookings/admin/payments`) → payment không cần lớp JWT / biến môi trường mới. Treo = PENDING > 15′.
  Kèm mã lỗi VNPay + lần đối soát gần nhất. Trang `/admin/payments`. *Còn*: REFUND_PENDING kẹt lâu (xem tạm ở `/admin/orders` lọc "Chờ hoàn tiền").
- [x] **ADM-4 · Trang `/admin/dashboard`** – *xong 2026-11-22*: KPI, 2 biểu đồ, đơn theo trạng thái, lý do huỷ, top sự kiện, lọc ngày/sự kiện/load test. Đơn hàng + Thanh toán làm thành trang riêng `/admin/orders`, `/admin/payments` (menu trái) thay vì tab (frontend, `RequireAdmin`, đặt làm trang mặc định của `/admin`):
  - Hàng KPI: doanh thu hôm nay / 7 ngày, vé đã bán, đơn đang chờ (PENDING + AWAITING_PAYMENT), đơn lỗi/hết hạn, tỷ lệ chuyển đổi.
  - Biểu đồ doanh thu + số vé theo ngày (dùng lại `admin/LineChart.tsx`); bảng top sự kiện (đã bán / tổng – nối CAT-2).
  - Tab "Đơn hàng" (ADM-2) và "Thanh toán lỗi" (ADM-3) có lọc trạng thái, tìm theo email/mã đơn, bấm xem chi tiết.
  - Bộ lọc khoảng ngày + sự kiện; tự làm mới 30 s.
- [ ] **ADM-5 · Thao tác admin** (sau khi xem được): hủy đơn treo, hoàn tiền thủ công (gọi `/internal/payments/bookings/{id}/refund`),
  xuất CSV đơn hàng. Mỗi thao tác ghi audit log (ai, lúc nào, lý do).
- [ ] **ADM-7 · Kho báo cáo (khi cần)**: CDC (Debezium/Kafka Connect) từ các DB → ClickHouse/warehouse riêng cho báo cáo chéo
  service (doanh thu × check-in × marketing). Làm khi một trang admin phải JOIN ≥ 2 service hoặc truy vấn thống kê chạm OLTP.
- [ ] **ADM-6 · Người dùng** (identity-service): danh sách user, khóa/mở (`ACTIVE`/`DISABLED`), xem đơn của 1 user.

Phụ thuộc/liên quan: CAT-2 (đã bán/tổng theo hạng vé – inventory), TIX-2 (check-in → vé USED, hoàn tiền → VOID).
Kiểm tra: compile + curl API + xem trên trình duyệt (chưa viết test – theo quy ước hiện tại).

## Đã xong
