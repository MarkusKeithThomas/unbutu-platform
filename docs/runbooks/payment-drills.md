# Runbook – luyện sự cố thanh toán (cổng giả lập)

Mục tiêu: tự tay gây lỗi IPN/ngân hàng thường gặp, quan sát hệ thống tự hồi phục, biết **xem ở đâu** khi có thật.
Chỉ chạy ở **local** (hoặc tạm bật trên prod khi diễn tập có báo trước). Bảng lỗi: `services/payment-service/README.md`.

## Chuẩn bị (local)
```bash
# Đối soát nhanh cho dễ quan sát (mặc định: 1m / 2m / 2m)
export PAYMENT_RECONCILE_INTERVAL=10s PAYMENT_RECONCILE_PENDING_AFTER=15s PAYMENT_RECONCILE_ABANDON_AFTER=5s MOCK_IPN_RETRY_BACKOFF=3s,5s
export MOCK_FAULT_IPN_DROP_RATE=1        # ← chọn 1 lỗi
docker compose -f deploy/compose.local.yml up -d --wait payment-service
```
Mua 1 vé trên UI (http://localhost:8080) → "Xác nhận thanh toán" → trang ngân hàng (dòng đỏ ghi lỗi đang bật) → bấm kết quả.
Xong: `unset MOCK_FAULT_…` rồi chạy lại lệnh compose để về happy case.

## Xem ở đâu
| Câu hỏi | Lệnh |
|---|---|
| Ngân hàng đã gửi IPN mấy lần, merchant trả gì? | `psql payment_db: select txn_ref, attempt, status, fault_note, last_result from mockbank_ipn_deliveries order by id desc limit 10;` |
| Đối soát đã làm gì? | `select txn_ref, action, gateway_code, gateway_status, note, checked_at from reconciliation_log order by id desc limit 10;` |
| Trạng thái giao dịch / đơn | `payment_db: select txn_ref, status, provider_response_code from payments …` · `booking_db: select code, status, cancel_reason from bookings …` |
| Log | `make logs s=payment-service` – tìm `GIẢ LẬP`, `IPN`, `ĐỐI SOÁT`; booking: `SettlementService` |
| Trên SigNoz (prod) | Logs: `service.name = payment-service AND body CONTAINS 'ĐỐI SOÁT'`; trace của IPN theo `trace_id` |

## Kỳ vọng (đã chạy thử 2026-11-21, đối soát 10s/15s)
| Lỗi | Kỳ vọng |
|---|---|
| IPN mất | đơn `AWAITING_PAYMENT` ~15–20 s → đối soát `SUCCEEDED` → `CONFIRMED` |
| IPN lặp 3 | deliveries: `00`, `02`, `02`; đơn chốt 1 lần |
| Mất mạng 2 lần | 1 delivery `3x DELIVERED` |
| IPN sai chữ ký / sai số tiền | deliveries `GAVE_UP 97` / `04`; đối soát `SUCCEEDED` → `CONFIRMED` |
| Ngân hàng chậm 30 s | đối soát `WAIT 00/01 ngân hàng đang xử lý`; IPN khi xong → `CONFIRMED` ~31 s |
| querydr lỗi (+ IPN mất) | đối soát `ERROR 99` mỗi lượt, đơn treo; hồi phục → `CONFIRMED` |
| Bỏ ngang trên trang ngân hàng | quá hạn → `FAILED ABANDONED`, đơn `EXPIRED`; khách vẫn trả → giao dịch mở lại `SUCCEEDED` → đơn `CONFIRMED` (hoặc hoàn tiền nếu hết vé) |

## Khi gặp thật trên prod
- Đơn treo `AWAITING_PAYMENT` > 5′: xem `reconciliation_log` của txn_ref đó. `ERROR` liên tục → cổng/querydr có vấn đề (kiểm `PAYMENT_API_URL`, chữ ký).
- `MISMATCH` trong log: ngân hàng báo số tiền khác đơn → **không tự xử lý**, đối chiếu tay với ngân hàng.
- Booking `REFUND_PENDING` lâu: payment refund lỗi → xem log booking `SettlementService` (consumer tự retry).
