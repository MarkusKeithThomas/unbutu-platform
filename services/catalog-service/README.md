# catalog-service

**Danh mục sự kiện / phim**

| | |
|---|---|
| Trách nhiệm | Sự kiện (event/movie), địa điểm (venue), phòng/khu (hall), suất diễn (showtime), loại vé & giá cơ bản. Đọc nhiều → cache. |
| Dữ liệu | Postgres `catalog_db`: events, venues, halls, seat_layouts, showtimes, ticket_types. |
| Event (Redpanda, ADR-0007) | Phát qua Outbox: topic `catalog.event.v1` (key = id sự kiện) – `event.upserted`, `event.deleted`, `ticket-type.upserted`, `ticket-type.deleted` (schema: `libs/event-contracts`). Redpanda tắt → API vẫn chạy, event chờ trong bảng `outbox`. |
| Gọi đồng bộ | – |
| Package | `com.unbutu.ticket.catalog` |
| Port trong container | 8080 |

**Đã có**: `events` (+ địa điểm, giờ bắt đầu), `ticket_types` (mã `TT-0001`, giá VND, tổng số vé, trạng thái `DRAFT|ON_SALE|PAUSED`).
API công khai `GET /api/catalog/events/**` (khách, `Cache-Control: public` 30 s – đừng đọc token/user ở đây để CDN cache được); API ghi `/api/catalog/admin/**` cần access token có role `ADMIN`
(verify bằng JWKS của identity – biến `IDENTITY_JWKS_URI`, mặc định `http://identity-service:8080/.well-known/jwks.json`).
Hạng vé đã từng mở bán (`first_on_sale_at`) không xoá hẳn / không về Nháp – có thể đã có đơn. Số đã bán: inventory-service (chưa có).

Cấp quyền admin (chưa có màn quản lý user): `UPDATE users SET roles = 'CUSTOMER,ADMIN' WHERE email = '…';` trong `identity_db`,
rồi đăng xuất/đăng nhập lại (role nằm trong access token).

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
