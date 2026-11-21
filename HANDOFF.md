# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng
- Bật kafka-ui trong tailnet: `! ssh unbutu 'sudo tailscale serve --bg --https=9443 http://127.0.0.1:8091 && tailscale serve status'`
  → Claude kiểm từ ngoài tailnet phải thất bại (`docs/runbooks/kafka-ui.md`).
- Release `d9dfb1f` (#39, chỉ đổi `deploy.sh` – đã cài tay lên server) chờ Approve: image không đổi, Approve hay để đó đều được.
- Chạy thử 1 đơn thật trên prod (giữ → thanh toán giả lập → CONFIRMED → 1 event `booking.confirmed`, xem trên kafka-ui).
- Funnel **công khai** `:10000` → `127.0.0.1:8090` (`sds-web-1`, ngoài repo này) – người dùng xác nhận có cố ý không.

## Làm tiếp
- **Load test** (người dùng định làm) – trước khi chặn cổng giả lập (PAY-1c (3), `docs/tasks/catalog.md`).
- **ticketing** (nghe `booking.event.v1` → vé QR) + **notification** (email). Mọi code: tự kiểm 50k rps; trước push chạy đủ như CI.

## Ghi chú
- Prod 10:35 `ef9537f` OK: static membership booking/inventory (Stable, lag 0), kafka-ui readOnly, kid prod vẫn `identity-1`.
- 10:21 deploy `65bf7a5` lỗi pull GHCR qua IPv6 ("connection reset") ~2′ → rollback; #39: 5 lần thử, chờ tăng dần, 2 image song song.
  Lặp lại nhiều → cân nhắc (hỏi người dùng) ưu tiên IPv4 cho Docker trên server.
- Rollback từ bản static membership về bản cũ: group chờ member cũ ≤ 60 s (runbook kafka-ui).
- Local: payment_db đã có migration PAY-1b. Smoke CI local cần cổng 8080 trống (`make down`). kafka-ui local: `localhost:8091`.
- Kịch bản thử (scratchpad, mất khi reboot): `pay-e2e.sh`, `pay-late.sh`, `pay-faults.sh <…>`. Docker Desktop hay tắt → `open -a Docker`.
- User test local: `admin@local.test` / `Password123` (ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
