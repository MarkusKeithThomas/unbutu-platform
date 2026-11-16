# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay
1. **OBS-2 – dựng metrics server + container.** Code ở **PR #15**. Khi #15 đã vào `main`: chạy `docs/runbooks/signoz.md` bước 1–2,
   rồi kiểm tra theo "Xong khi" của OBS-2 trong `docs/tasks/observability.md`. Đối chiếu `filesystem /` với `df -h /`;
   `docker_stats` lỗi → `docker logs signoz-ingester`.

## Đang chờ người dùng
- Merge PR #15 (OBS-2) và PR docs `docs/retention-15d`.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
