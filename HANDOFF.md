# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-22.

## Làm tiếp – Load test (`docs/tasks/loadtest.md`, runbook `docs/runbooks/loadtest.md`)
1. **Rollout LT-B + LT-C** (#45 loadtest-service, #47 mua vé end-to-end – đã merge; secret + `loadtest_db` + env-map
   pro-ticket-workload #8/#9 đã xong 2026-11-22): người dùng **Approve bản Deploy mới nhất** (chứa #47) → kiểm tra
   `https://unbutu.tail3c11f4.ts.net/admin/loadtest`: `browse` 50,100 rồi `purchase` 2,5 (sự kiện ẩn tự tạo, xong lượt PAUSED).
2. **LT-A**: dò điểm gãy `browse` + `purchase` → `docs/loadtest/baseline.md`. Trần giả: IPN giả lập tuần tự ≤ 50/s.
3. Sau: sửa trần IPN giả lập; flash-sale kho nhỏ kiểm oversell; LT-E k6 → SigNoz + dashboard "Load test".
   Giao diện `/admin/loadtest` + công tắc "Ẩn khỏi khách" mới kiểm bằng tsc – người dùng xem trên trình duyệt.

## Song song / sau
- TIX-2: check-in, email vé, `booking.refunded` → VOID (`docs/tasks/catalog.md`).
- **Admin** (`docs/tasks/admin.md`): ADM-2/3 ở nhánh `feat/admin-orders-payments` (migration booking V6, payment V4) → sau deploy kiểm
  `/admin/orders`, `/admin/payments`. Tiếp: vé QR trong chi tiết đơn (notification admin API), ADM-5 thao tác admin.

## Ghi chú
- k6 REST API trả `Content-Type: text/plain` → loadtest-service đọc String rồi parse. Số liệu trực tiếp không có p99 (chỉ p90/p95).
- zsh: `"$svc:ci"` bị hiểu là modifier → build image tag bằng `bash -c` hoặc `"${svc}:ci"`.
- Local DB có sẵn volume → DB service mới tạo tay (`CREATE USER … ; CREATE DATABASE …_db …`); `loadtest_db` local đã tạo.
- Smoke local: image `:ci` có thể dính cache cũ → `docker build --no-cache` khi nghi ngờ; cần `make down` (cổng 8080).
- Funnel công khai `:10000` → `127.0.0.1:8090` (`sds-web-1`, ngoài repo) – người dùng chưa xác nhận có cố ý không.
- kafka-ui prod: `https://unbutu.tail3c11f4.ts.net:9443` (chỉ đọc, tailnet). Local `localhost:8091`.
- User test local: `admin@local.test` / `Password123` (ADMIN). Docker Desktop hay tắt → `open -a Docker`.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
