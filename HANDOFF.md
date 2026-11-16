# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay
1. **OBS-7 – đo CPU ClickHouse** từ **16:56 UTC 2026-11-16** trở đi (stack tạo lại lúc ~16:26, bản `18cf1f1`):
   `ssh unbutu 'for i in 1 2 3; do docker stats --no-stream --format "{{.CPUPerc}}" signoz-telemetrystore-clickhouse-0-0; done'`
   + đếm part mới của `system` / 30 phút trong `system.part_log`. Đạt (< 5%) hoặc ghi mức đo + lý do → đóng OBS-7.
2. Sau đó gợi ý người dùng chọn việc tiếp theo: **OBS-5** (dashboard RED + SLO + alert Telegram) hoặc Gateway P0 còn lại (JWT, CORS).

## Đang chờ người dùng
- Merge PR docs `docs/obs-3-4-done`.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
