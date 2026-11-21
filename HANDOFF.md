# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng
- Approve deploy **PAY-1c** (#36, Release đang chờ) rồi merge + Approve PR **static membership inventory + kid identity local**
  (nhánh `fix/jwt-kid-and-inventory-static-membership`). Không cần đổi pro-ticket-workload.
  Sau deploy Claude kiểm: topic `booking.event.v1` có trên Redpanda prod (`rpk topic list`); log booking/inventory sau deploy:
  `instanceId=booking-1-payment-0` / `inventory-1-catalog-0`, không có "pro-actively leaving" kèm generation tăng.
- Chạy thử 1 đơn thật trên prod (giữ → thanh toán giả lập → CONFIRMED → 1 event `booking.confirmed`) – cần tài khoản người dùng.

## Làm tiếp
- **Load test** (người dùng định làm) – trước khi chặn cổng giả lập (PAY-1c (3), `docs/tasks/catalog.md`).
- **ticketing** (nghe `booking.event.v1` → vé QR) + **notification** (email). Mọi code: tự kiểm 50k rps; trước push chạy đủ như CI.

## Ghi chú
- Local: payment_db đã có migration PAY-1b – chạy code payment cũ hơn sẽ lỗi `vnp_create_date` NOT NULL.
- Luyện sự cố thanh toán: `docs/runbooks/payment-drills.md` (local: export MOCK_FAULT_* rồi up payment-service).
- Kịch bản thử (scratchpad, mất khi reboot): `pay-e2e.sh`, `pay-late.sh`, `pay-faults.sh <drop|duplicate|network|badsig|wrongamount|pending|querydr|abandon>`.
- Smoke CI local (`tools/ci/compose-smoke.sh`) cần cổng 8080 trống → `make down` trước.
- Docker Desktop hay tự tắt → `open -a Docker`. User test local: `admin@local.test` / `Password123` (ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
