# Load test mua vé end-to-end trên prod – 2026-11-22

Lượt `2c003576` (kịch bản `purchase`, bậc 10 → 20 → 30 lượt mua/giây, mỗi bậc 30 s, p95 request < 1500 ms,
tới lúc có QR p95 < 5000 ms). Prod `e6200ad`, máy `unbutu` 4 core / 11 GiB, k6 chạy cùng máy (`cpus: 1`).

## Kết quả
| Bậc | Đạt | p95 request | Bỏ lượt | Tới lúc có QR p95 | % có vé | Kết quả |
|---|---|---|---|---|---|---|
| 10/s | 10/s | 59 ms | 0 | 2,7 s | 100% | PASS |
| 20/s | 11/s | 919 ms | 112 | **13,4 s** | 100% | FAIL (bỏ lượt, tới lúc có QR) |
| 30/s | – | 1,9 s | 51 | – | – | FAIL (dừng sớm) |

**Chịu được ~10 lượt mua/giây** (≈ 60 request/giây). Bậc 20 FAIL **không vì p95 request** mà vì chuỗi bất đồng bộ sau
thanh toán chậm → mỗi lượt giữ người dùng ảo lâu → k6 hết VU → bỏ lượt.

## Thời gian từng chặng (đo từ DB: bookings, payments/mockbank, tickets)
| Chặng | Bậc 10/s p50 / p95 | Bậc 20/s p50 / p95 |
|---|---|---|
| Giữ vé → bấm thanh toán (đồng bộ) | 0,09 / 0,17 s | 0,33 / 0,82 s |
| Bấm → IPN xử lý xong (`paid_at`) | 0,71 / 1,34 s | 1,66 / 3,73 s |
| Paid → CONFIRMED (`payment.result.v1` → booking) | 0,37 / 0,72 s | 1,72 / 3,40 s |
| CONFIRMED → có QR (`booking.event.v1` → notification) | 0,25 / 0,38 s | 0,67 / **9,87 s** |
| **Tổng** | 1,44 / 2,20 s | 5,41 / **15,44 s** |

## Nghẽn ở đâu
1. **Consumer Kafka chạy 1 luồng cho cả 6 partition** (notification `booking.event.v1`, booking `payment.result.v1`).
   Trace: `booking.event.v1 process` p50 32 ms / p95 183 ms (INSERT tickets chỉ 4 ms – phần còn lại: ký QR ES256, JSON,
   commit, chờ CPU) → ~10–11 message/giây. Ở bậc 20 đầu vào đỉnh ~15,5 `booking.confirmed`/giây → hàng đợi dồn ~150 message
   → trễ ~10–15 s. booking `payment.result.v1 process` p50 47 / p95 106 ms – cùng kiểu.
   Lag đo mỗi 30 s nên hiện 0; tốc độ đọc (`kafka.consumer.records_consumed_rate` ≈ 10,5/s) mới là dấu hiệu.
2. **CPU cả máy gần cạn**: khung 05:28:20–05:29:00 tổng ~370% / 400%. Lớn nhất: booking 74%, SigNoz (ingester + ClickHouse)
   ~140% (log + trace của mọi request), payment 46%, notification 41–53%, inventory 24%, gateway 17–36%, k6 11–29%.
3. **Không phải nghẽn**: Outbox relay (booking p95 0,3 s, payment ≤ 1,6 s), commit offset (5–8 ms), IPN giả lập
   (theo kịp số lượt bấm, ~15–18/s – trần 50/s chưa chạm), p95 request đồng bộ.

## Việc nên làm (chưa làm – chờ người dùng chọn)
- Listener concurrency = 3–6 cho `booking.event.v1` (notification) và `payment.result.v1` (booking) – giữ thứ tự theo
  partition (key = bookingId). Rẻ, hiệu quả nhất.
- Giảm CPU của observability lúc test: sampling trace, bớt log INFO mỗi request/mỗi vé.
- k6 chạy máy khác (Mac qua tailnet) để không tranh CPU với hệ thống bị đo.
- Chạy lại sau mỗi thay đổi, so với bảng trên.
