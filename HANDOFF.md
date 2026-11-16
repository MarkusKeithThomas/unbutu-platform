# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay (khi PR OBS-3/4/7 đã vào `main`)
1. Dựng lại observability: `docs/runbooks/signoz.md` bước 1 → `up -d --force-recreate --wait`. Lý do: đổi config ClickHouse + collector.
2. Nhắc người dùng **Approve** deploy prod (image mới có OTel agent). Kiểm tra `deploy-history.log` = ok.
3. Kiểm chứng theo "Xong khi" của **OBS-3 + OBS-4** và **OBS-7** trong `docs/tasks/observability.md`.
   Trước hết xem attribute lồng nhau của log JSON lưu ra sao (chưa kiểm được trước merge).

## Đang chờ người dùng
- Merge PR OBS-3/4/7. Merge xong rồi mới Approve deploy prod.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
