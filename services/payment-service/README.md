# payment-service

**Thanh toán (cổng giả lập)**

| | |
|---|---|
| Trách nhiệm | Tạo payment intent, **cổng thanh toán mock** (cấu hình tỉ lệ lỗi/độ trễ để luyện sự cố), webhook callback, idempotency, hoàn tiền. |
| Dữ liệu | Postgres `payment_db`: payments, refunds, outbox, processed_events. |
| Event (RabbitMQ) | Nghe: `booking.created`. Phát: `payment.succeeded`, `payment.failed`, `payment.refunded`. |
| Gọi đồng bộ | – |
| Package | `com.unbutu.ticket.payment` |
| Port trong container | 8080 |

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
