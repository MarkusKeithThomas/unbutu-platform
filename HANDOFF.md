# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng
- Merge PR **PAY-1b** (nhánh `feat/pay-1b-reconcile-faults-main`) → Approve deploy. Không cần đổi pro-ticket-workload
  (env mới đều có mặc định = happy case; migration V2, V3 của payment tự chạy).
  Sau deploy Claude kiểm: `reconciliation_log` có bản ghi khi có đơn treo; log payment "Ngân hàng giả lập: lỗi đang bật = []".
- Chạy thử 1 đơn thật trên prod (giữ → thanh toán giả lập → CONFIRMED) – cần tài khoản người dùng.

## Làm tiếp
- **PAY-1c** (`docs/tasks/catalog.md`): Outbox `booking.confirmed`, static membership consumer booking, chặn cổng giả lập trên prod.
- Rồi: ticketing (vé QR) + notification (email). Mọi code: tự kiểm 50k rps; trước push chạy đủ như CI.

## Ghi chú
- Prod 2026-11-21 08:21: deploy PAY-1 fail → rollback (nhiều khả năng payment_db chưa tạo), 08:33 deploy lại OK.
- Luyện sự cố thanh toán: `docs/runbooks/payment-drills.md` (local: export MOCK_FAULT_* rồi up payment-service).
- Kịch bản thử (scratchpad, mất khi reboot): `pay-e2e.sh`, `pay-late.sh`, `pay-faults.sh <drop|duplicate|network|badsig|wrongamount|pending|querydr|abandon>`.
- Docker Desktop hay tự tắt → `open -a Docker`.
- User test local: `admin@local.test` / `Password123` (ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
