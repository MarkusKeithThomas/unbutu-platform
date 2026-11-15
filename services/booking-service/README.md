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

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
