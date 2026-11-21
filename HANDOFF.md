# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng
- Review + merge PR **ORD-1a** (Redpanda + Outbox catalog, nhánh `feat/ord-1a-redpanda-outbox`) → Approve deploy.
  Deploy này **thêm container `redpanda` lên prod** (mem_limit 1,25 GB, đo ~100 MB). Sau deploy kiểm tra:
  `ssh unbutu 'docker exec prod-redpanda-1 rpk topic list; docker logs prod-catalog-service-1 2>&1 | grep Outbox: | tail -2'`
  → phải thấy `catalog.event.v1` + "đã gửi N event" (backfill dữ liệu prod).
- Đổi mật khẩu: người dùng nói `hthuongtn2905@gmail.com` – prod KHÔNG có; chỉ có `chthuongtn2905@gmail.com` → chờ xác nhận đúng email.

## Làm tiếp
1. **ORD-1b** (`docs/tasks/catalog.md`): Redis vào compose + inventory-service (consumer `catalog.event.v1`, A8b availability, reservations Lua, sổ cái Postgres).
2. ORD-1c booking + UI bước 2 → rồi **PAY-1** (cổng giả lập, quyết định đã chốt). Mọi code: tự kiểm mở rộng 50k rps.

## Ghi chú local
- Kafka local: `localhost:19092` (IntelliJ) / `redpanda:9092` (container). Xem topic: `docker exec ticket-local-redpanda-1 rpk topic describe catalog.event.v1 -p`
  (`rpk topic consume` không có `-n` sẽ chờ mãi).
- DB local cũ đã sửa tay (checksum V1 catalog, user/DB identity). Lỗi Flyway → `make clean`.
- User test local: `admin@local.test` / `Password123` (ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
