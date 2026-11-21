# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Rollout TIX-1 – vé QR (notification-service mới, ADR-0009) – ĐÚNG THỨ TỰ, chưa Approve deploy trước bước 4
1. Người dùng tạo secret `unbutu/prod/notification-service` (`DB_PASSWORD`, `QR_SIGNING_KEY`) – lệnh pipe trong
   `docs/tasks/catalog.md` TIX-1 / tin nhắn cuối phiên (value không in ra màn hình).
2. PR pro-ticket-workload: `prod/services/notification-service/env.yaml` + postgres (`SERVICE_DATABASES` + `NOTIFICATION_DB_PASSWORD`)
   → plan xanh (cần secret ở bước 1) → merge + apply.
3. Claude: `docs/runbooks/add-service-database.md` với `svc=notification`.
4. Người dùng Approve deploy. Claude kiểm: log "Khoá ký QR sẵn sàng kid=qr-1", group `notification-service` Stable,
   vé được phát cho các đơn CONFIRMED cũ (topic giữ 30 ngày), `/api/tickets/.well-known/jwks.json` 200.

## Làm tiếp
- **Load test** (người dùng) – trước khi chặn cổng giả lập (PAY-1c (3)).
- TIX-2: check-in, email vé, `booking.refunded` → VOID (`docs/tasks/catalog.md`).

## Ghi chú
- Local DB có sẵn volume → DB service mới tạo tay (`CREATE USER notification …; CREATE DATABASE notification_db …`).
- Smoke local: image `:ci` có thể dính cache cũ → `docker build --no-cache` khi nghi ngờ; cần `make down` (cổng 8080).
- Funnel công khai `:10000` → `127.0.0.1:8090` (`sds-web-1`, ngoài repo) – người dùng xác nhận có cố ý không.
- kafka-ui prod: `https://unbutu.tail3c11f4.ts.net:9443` (chỉ đọc, tailnet). Local `localhost:8091`.
- Kịch bản thử (scratchpad, mất khi reboot): `pay-e2e.sh` (in `(tmp: …)` chứa token `u` + bookingId `id`).
- User test local: `admin@local.test` / `Password123` (ADMIN). Docker Desktop hay tắt → `open -a Docker`.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
