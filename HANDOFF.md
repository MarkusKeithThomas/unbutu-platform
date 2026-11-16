# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay
1. **OBS-2 – metrics server + container** (đã chốt: docker-socket-proxy, không mount `docker.sock` vào collector).
   Nhánh `feat/obs-2-host-docker-metrics`. Chi tiết + tiêu chí "Xong khi": `docs/tasks/observability.md` → OBS-2.

## Đang chờ người dùng
- Merge PR #14 (`docs/tasks/observability.md` + file này).
- **OBS-1:** đặt retention 7 ngày trên UI và **bấm Save**. Lần kiểm tra 2026-11-16 vẫn là 15/15/30 ngày; query kiểm tra có trong OBS-1.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
