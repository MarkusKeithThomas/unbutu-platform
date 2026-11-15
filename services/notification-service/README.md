# notification-service

**Thông báo**

| | |
|---|---|
| Trách nhiệm | Gửi email/Telegram xác nhận đặt vé, vé điện tử. Chỉ tiêu thụ event, consumer idempotent. |
| Dữ liệu | Postgres `notification_db`: notifications, processed_events. |
| Event (RabbitMQ) | Nghe: `user.registered`, `booking.confirmed`, `booking.cancelled`, `payment.refunded`. |
| Gọi đồng bộ | – |
| Package | `com.unbutu.ticket.notification` |
| Port trong container | 8080 |

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
