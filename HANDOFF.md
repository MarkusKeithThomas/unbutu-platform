# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng (theo thứ tự)
1. Merge **PR #31** (ORD-1a Redpanda + Outbox) → Approve deploy. Sau deploy Claude kiểm:
   `ssh unbutu 'docker exec prod-redpanda-1 rpk topic list; docker logs prod-catalog-service-1 2>&1 | grep Outbox: | tail -2'`
2. Rollout **ORD-1b** (PR inventory, nhánh `feat/ord-1b-inventory`, base = nhánh #31):
   a. Người dùng tạo secret AWS `unbutu/prod/inventory-service`, key `DB_PASSWORD` (vd. `openssl rand -hex 24`).
   b. Merge + apply **pro-ticket-workload PR #5** (env-map inventory, postgres `SERVICE_DATABASES`).
   c. Claude chạy `docs/runbooks/add-service-database.md` (tạo user/DB `inventory` trên prod).
   d. Khi #31 đã merge: Claude tạo nhánh mới từ `main` + cherry-pick commit ORD-1b (không force-push được), mở PR mới, đóng PR cũ.
   e. Merge PR inventory → Approve deploy (thêm container `redis` + `inventory-service`).

## Làm tiếp
- **ORD-1c** booking-service + UI bước 2 (`docs/tasks/catalog.md`) → rồi **PAY-1**. Mọi code: tự kiểm 50k rps.

## Ghi chú local
- DB local cũ sửa tay (checksum V1 catalog, user/DB identity + inventory tạo tay). Lỗi Flyway/DB → `make clean`.
- Kafka `localhost:19092`, Redis `localhost:6379`, inventory `localhost:8083` (API nội bộ gọi thẳng port này để thử).
- `rpk topic consume` không có `-n` sẽ chờ mãi.
- User test local: `admin@local.test` / `Password123` (ADMIN). Local có hạng vé thử "Oversell test …", "Semantics …".

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
