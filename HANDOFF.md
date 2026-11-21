# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng – ROLLOUT PAY-1 (thứ tự)
1. ✅ Secret `unbutu/prod/payment-service` (DB_PASSWORD, PAYMENT_HASH_SECRET – value ngẫu nhiên, người dùng sẽ đổi).
2. Merge + apply **pro-ticket-workload #6** (env-map payment, postgres `SERVICE_DATABASES` + payment).
3. Claude tạo DB `payment` trên prod: `docs/runbooks/add-service-database.md` (svc=payment).
4. Merge PR **PAY-1** (nhánh `feat/pay-1-mock-gateway`) → Approve deploy → Claude kiểm prod (IPN 97 khi sai chữ ký, trang cổng giả lập).
   Chạy thử 1 đơn thật trên prod cần tài khoản người dùng (giữ → thanh toán giả lập → CONFIRMED).

## Lưu ý
- Cổng giả lập bật trên prod = ai cũng "thanh toán" được (không trừ tiền thật) → vé CONFIRMED miễn phí. Chấp nhận ở giai đoạn này;
  PAY-1b: chặn / tắt khi có VNPay sandbox (`docs/tasks/catalog.md`).

## Làm tiếp
- **PAY-1b** (đối soát giao dịch treo, Outbox booking.*, VNPay sandbox). Mọi code: tự kiểm 50k rps; trước push chạy đủ như CI.

## Ghi chú local
- DB local sửa tay (checksum V1 catalog; user/DB identity, inventory, booking, payment tạo tay). Lỗi Flyway/DB → `make clean`.
- Port: gateway 8080, identity 8081, catalog 8082, inventory 8083, booking 8084, payment 8085; Kafka 19092; Redis 6379.
- Kịch bản thử (scratchpad, mất khi reboot): `pay-e2e.sh` (thành công/thất bại/IPN giả mạo), `pay-late.sh` (trả muộn còn/hết vé).
- Luyện sự cố local: `export PAYMENT_MOCK_IPN_DELAY=20s` hoặc `PAYMENT_MOCK_IPN_DROP_RATE=0.5` trước `make up`.
- User test local: `admin@local.test` / `Password123` (ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
