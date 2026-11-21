# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-22.

## Làm tiếp – Load test (`docs/tasks/loadtest.md`, runbook `docs/runbooks/loadtest.md`)
1. **LT-A** (người dùng): `make loadtest STEPS=100,200,400,800` (Terminal thật – cần TTY cho ssh -t), mở http://localhost:5665;
   dò quanh bậc FAIL; Claude đọc SigNoz (Service RED + Container) tìm nghẽn → `docs/loadtest/baseline.md`.
2. LT-B kịch bản giữ vé / mua vé; LT-C nút bấm trên web (khi cần); LT-D alert SigNoz (hoãn).

## Song song / sau
- TIX-2: check-in, email vé, `booking.refunded` → VOID (`docs/tasks/catalog.md`).

## Ghi chú
- Local DB có sẵn volume → DB service mới tạo tay (`CREATE USER … ; CREATE DATABASE …_db …`).
- Smoke local: image `:ci` có thể dính cache cũ → `docker build --no-cache` khi nghi ngờ; cần `make down` (cổng 8080).
- Funnel công khai `:10000` → `127.0.0.1:8090` (`sds-web-1`, ngoài repo) – người dùng chưa xác nhận có cố ý không.
- kafka-ui prod: `https://unbutu.tail3c11f4.ts.net:9443` (chỉ đọc, tailnet). Local `localhost:8091`.
- User test local: `admin@local.test` / `Password123` (ADMIN). Docker Desktop hay tắt → `open -a Docker`.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
