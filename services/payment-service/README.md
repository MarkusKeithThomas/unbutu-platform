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

## Ngân hàng giả lập + lỗi giả lập (PAY-1b)
Cổng giả lập là một "ngân hàng" có sổ sách riêng (`mockbank_transactions`), hàng đợi IPN bền (`mockbank_ipn_deliveries`,
retry theo lịch `MOCK_IPN_RETRY_BACKOFF` khi merchant lỗi / timeout) và API **querydr** đúng VNPay merchant_webapi.
**Mặc định happy case.** Lỗi thường gặp bật bằng env (tỉ lệ 0..1); mỗi lỗi = 1 lớp `IpnFault` trong `mock/fault/` –
thêm lỗi mới = thêm 1 lớp + 1 dòng `payment.mock.faults.*`, không sửa ngân hàng giả lập.

| Env | Mô phỏng | Ai cứu |
|---|---|---|
| `MOCK_FAULT_IPN_DROP_RATE` | IPN không bao giờ tới | Đối soát (querydr) |
| `MOCK_FAULT_IPN_DELAY` (vd. `30s`) | IPN tới chậm – UI "đang xác nhận" | Chờ IPN |
| `MOCK_FAULT_IPN_DUPLICATE_RATE` | IPN gửi 3 lần | Idempotency (RspCode 02) |
| `MOCK_FAULT_IPN_NETWORK_ERROR_RATE` | 2 lần gửi đầu mất mạng | Ngân hàng retry |
| `MOCK_FAULT_IPN_BAD_SIGNATURE_RATE` | IPN sai chữ ký thay bản thật | Merchant từ chối 97 → đối soát |
| `MOCK_FAULT_IPN_WRONG_AMOUNT_RATE` | IPN sai số tiền (chữ ký đúng) | Merchant từ chối 04 → đối soát |
| `MOCK_FAULT_BANK_PENDING_RATE` + `_DURATION` | Ngân hàng "đang xử lý" rồi mới có kết quả | Đối soát chờ (WAIT) → IPN khi xong |
| `MOCK_FAULT_QUERYDR_ERROR_RATE` | API querydr lỗi 99 | Đối soát thử lại lượt sau |
| `PAYMENT_MOCK_ALLOW_LATE` | Khách trả sau hạn giữ / sau khi bị kết luận bỏ ngang | Mở lại giao dịch → booking trả muộn |

## Đối soát (`ReconciliationJob`)
Mỗi `PAYMENT_RECONCILE_INTERVAL` (1′): payment `PENDING` quá `PAYMENT_RECONCILE_PENDING_AFTER` (2′) → querydr → áp kết quả
**cùng đường IPN** (`settle`, Outbox). Khách chưa trả mà quá hạn giữ + `PAYMENT_RECONCILE_ABANDON_AFTER` → `FAILED(ABANDONED|NOT_PAID)`.
Mỗi lần hỏi ghi `reconciliation_log`. Hướng dẫn luyện: `docs/runbooks/payment-drills.md`.

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
