# booking-service

**Đơn đặt vé – điều phối Saga**

| | |
|---|---|
| Trách nhiệm | Tạo booking, điều phối Saga: hold ghế → tạo booking PENDING → chờ thanh toán → CONFIRMED (xác nhận ghế) hoặc CANCELLED/EXPIRED (nhả ghế). Outbox pattern, idempotency key cho POST. Phát hành vé (mã QR). |
| Dữ liệu | Postgres `booking_db`: bookings, booking_items, tickets, outbox. |
| Event (RabbitMQ) | Phát: `booking.created`, `booking.confirmed`, `booking.cancelled`. Nghe: `payment.succeeded`, `payment.failed`. |
| Gọi đồng bộ | Gọi `inventory-service` (hold/confirm/release), `catalog-service` (giá). |
| Package | `com.unbutu.ticket.booking` |
| Port trong container | 8080 |

## Đã có (ORD-1c, 2026-11-21)
| API (JWT) | |
|---|---|
| `POST /api/bookings` + `Idempotency-Key` | Giữ vé 10′ → `201` đơn `PENDING` (`code` ORD-xxxxxx, `expiresAt`, dòng giá, phí 10.000 đ/vé). Cùng key → `200` cùng đơn; khác nội dung → `409 IDEMPOTENCY_CONFLICT`. `409 SOLD_OUT\|NOT_ON_SALE {ticketTypeId}`, `422 LIMIT_EXCEEDED {remaining}` (4 vé / tài khoản / sự kiện), `503 INVENTORY_UNAVAILABLE` |
| `GET /api/bookings/{id}` | Chỉ chủ đơn (người khác → 404) |
| `GET /api/bookings/me?page=&size=` | Mới trước, Slice (không COUNT) |
| `DELETE /api/bookings/{id}` | Huỷ + nhả vé, idempotent |

- Thứ tự A9: transaction ngắn (idempotency → `pg_advisory_xact_lock(user, sự kiện)` → giới hạn → giá từ bảng sao chép → INSERT PENDING) → **commit** → gọi inventory **ngoài transaction** (không giữ kết nối DB khi gọi mạng). Hết vé/lỗi → `CANCELLED`.
- Bảng giá + thông tin sự kiện: consumer `catalog.event.v1` (group `booking-service`), snapshot có version.
- Hết hạn: `BookingExpiryJob` 5 s – `UPDATE … WHERE id IN (… FOR UPDATE SKIP LOCKED)` → `EXPIRED` + nhả vé (inventory sweeper là lớp dự phòng).
- **PAY-1**: `POST /api/bookings/{id}/checkout {method, buyer}` → `AWAITING_PAYMENT` + `paymentUrl` (bấm lại → cùng giao dịch).
  Nghe `payment.result.v1`: `succeeded` → inventory **chốt vé** (`/internal/.../commit`) → `CONFIRMED`; không còn đủ vé (trả muộn) / sai số tiền → `REFUND_PENDING` → hoàn tiền → `REFUNDED`; `failed` → `PENDING` (thanh toán lại). `AWAITING_PAYMENT` có 5′ ân hạn trước khi `EXPIRED`.
- **PAY-1c**: `CONFIRMED` → Outbox (`libs/outbox`, cùng transaction với `UPDATE … → CONFIRMED`) → topic `booking.event.v1`
  (key = bookingId, 6 partition, giữ 30 ngày), type `booking.confirmed`. Event "béo" (người mua, sự kiện, từng hạng vé)
  → ticketing / notification không gọi ngược booking. Event lặp → UPDATE không đổi dòng → không phát lần 2.
- **Static membership**: mỗi listener có `group.instance.id` cố định `${booking.kafka.instance-id}-payment|-catalog`
  (env `KAFKA_GROUP_INSTANCE_ID`, mặc định `booking-1`), `session.timeout.ms` 60 s → deploy/restart không rebalance.
  Scale nhiều bản cùng lúc: mỗi bản một id khác (`booking-1..N`), trùng id → broker fence bản cũ.

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
