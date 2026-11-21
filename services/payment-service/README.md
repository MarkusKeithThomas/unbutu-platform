# payment-service

**Thanh toán (cổng giả lập)**

| | |
|---|---|
| Trách nhiệm | Tạo payment intent, **cổng thanh toán mock** (cấu hình tỉ lệ lỗi/độ trễ để luyện sự cố), webhook callback, idempotency, hoàn tiền. |
| Dữ liệu | Postgres `payment_db`: payments, refunds, outbox, processed_events. |
| Event (Redpanda) | Phát (Outbox): `payment.result.v1` key = bookingId – `payment.succeeded`, `payment.failed`, `payment.refunded`. |
| Gọi đồng bộ | – |
| Package | `com.unbutu.ticket.payment` |
| Port trong container | 8080 |

## Đã có (PAY-1, 2026-11-21)
Giao thức **VNPay 2.1.0** (ký HMAC-SHA512, `vnp_*`), hiện chạy **cổng giả lập** – đổi sang VNPay sandbox chỉ đổi `PAYMENT_PAY_URL`, `PAYMENT_TMN_CODE`, hash secret, `PAYMENT_MOCK_ENABLED=false`.

| API | |
|---|---|
| `POST /internal/payments` (booking gọi) | 1 giao dịch `PENDING` / đơn (khoá theo đơn); bấm lại cùng số tiền + phương thức → cùng giao dịch; đã trả → `409 ALREADY_PAID`; hết hạn giữ → `409 HOLD_EXPIRED`. Trả `paymentUrl` đã ký |
| `POST /internal/payments/bookings/{id}/refund` | Hoàn tiền (giả lập: ngay), idempotent → `payment.refunded` |
| `GET /api/payments/vnpay/ipn` | **Nguồn sự thật**: verify chữ ký (97), tìm đơn (01), số tiền (04), đã xử lý (02) → `SUCCEEDED/FAILED` + Outbox `payment.result.v1` trong 1 transaction. Luôn HTTP 200 + `{RspCode, Message}` |
| `GET /api/payments/vnpay/return` | Verify chữ ký → 302 về UI `/orders/{bookingId}`. Không đổi trạng thái |
| `GET/POST /api/payments/mock/pay` | Trang "ngân hàng giả lập": Thành công / Thất bại (51) / Huỷ (24); gửi IPN server→server rồi redirect return |

Luyện sự cố (env, local: export trước `make up`): `PAYMENT_MOCK_IPN_DELAY` (IPN chậm), `PAYMENT_MOCK_IPN_DROP_RATE` (IPN mất → cần đối soát PAY-1b), `PAYMENT_MOCK_ALLOW_LATE` (trả sau hạn giữ).

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
