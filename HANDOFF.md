# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-16.

## Làm ngay (khi PR `fix/obs-flatten-attrs-dedupe-requestid` đã vào `main`)
1. Dựng lại observability: `docs/runbooks/signoz.md` bước 1 → `up -d --force-recreate --wait`. Lý do: đổi `ingester.yaml` + ClickHouse config.
   **Ngay sau đó** kiểm tra collector không lỗi (`docker logs signoz-ingester`, OTTL `flatten` mới thêm). Nếu lỗi: dựng lại bằng config của `0181019`.
2. Nhắc người dùng **Approve** deploy prod (gateway sửa header trùng).
3. Kiểm chứng "Xong khi" của **OBS-3 + OBS-4** (attribute phẳng, 1 header) và **OBS-7** (đo CPU sau ≥ 30 phút).

## Đang chờ người dùng
- Merge PR sửa ở trên. Merge xong thì chờ Claude dựng lại observability rồi mới Approve deploy.

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
