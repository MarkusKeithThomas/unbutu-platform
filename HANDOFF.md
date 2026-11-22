# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-22.

## Làm tiếp – Load test (`docs/tasks/loadtest.md`, runbook `docs/runbooks/loadtest.md`)
1. **LT-C rollout prod** (`loadtest-service` + `/admin/loadtest`, PR #45 đã merge) – theo thứ tự, **chưa Approve deploy trước bước c**:
   a. Người dùng tạo secret `unbutu/prod/loadtest-service` key `DB_PASSWORD` (AWS Console, ap-southeast-1).
   b. Merge PR pro-ticket-workload #8 (env.yaml + `SERVICE_DATABASES` loadtest) → plan/apply xanh.
   c. Tạo `loadtest_db`: `docs/runbooks/add-service-database.md` với `svc=loadtest`.
   d. Approve Deploy bản mới nhất → mở `https://unbutu.tail3c11f4.ts.net/admin/loadtest`, chạy `browse` 50,100 thử.
2. **LT-A**: dùng trang đó dò điểm gãy `browse` (vd. 100,200,400,800) → `docs/loadtest/baseline.md`.
3. LT-B kịch bản mua vé (sự kiện ẩn + tài khoản test được miễn giới hạn 4 vé – cần sửa catalog/identity/booking);
   LT-E k6 → SigNoz + dashboard "Load test".

## Song song / sau
- TIX-2: check-in, email vé, `booking.refunded` → VOID (`docs/tasks/catalog.md`).

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
