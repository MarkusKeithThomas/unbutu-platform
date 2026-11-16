# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay
1. **OBS-2 – metrics server + container:** code xong ở **PR #15** (socket proxy chỉ-đọc, `hostmetrics`, `cap_drop: ALL`).
   Khi PR #15 đã merge: dựng lại theo `docs/runbooks/signoz.md` bước 1–2, rồi kiểm tra theo "Xong khi" của OBS-2.
   Đối chiếu `filesystem /` với `df -h /`. Nếu `docker_stats` lỗi API, xem `docker logs signoz-ingester`.

## Đang chờ người dùng
- Merge PR #14 (`docs/tasks/observability.md` + file này), sau đó PR #15 (OBS-2).
- **OBS-1:** đặt retention 7 ngày trên UI và **bấm Save**. Lần kiểm tra 2026-11-16 vẫn là 15/15/30 ngày; query kiểm tra có trong OBS-1.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
