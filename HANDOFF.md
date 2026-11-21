# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng – ROLLOUT inventory + booking (đã gộp: unbutu-platform #33 + pro-ticket-workload #5)
1. ✅ Secret AWS `unbutu/prod/inventory-service`, `unbutu/prod/booking-service` (Claude tạo, value ngẫu nhiên – người dùng sẽ đổi;
   đổi thì đổi cả password user Postgres – runbook mục "rotate").
2. ✅ pro-ticket-workload #5 đã merge + apply.
3. ✅ DB `inventory_db`, `booking_db` đã tạo trên prod.
4. **#33** draft → Ready → merge → Approve deploy → Claude kiểm prod.
   Bài học #32: PR xếp chồng merge vào nhánh cũ sau khi PR dưới squash → mất khỏi main. Chỉ mở PR khi base là `main`.

## Làm tiếp
- **PAY-1** – cổng giả lập (quyết định đã chốt trong `docs/tasks/catalog.md`). Mọi code: tự kiểm 50k rps; trước push chạy đủ như CI.

## Ghi chú local
- DB local cũ sửa tay (checksum V1 catalog; user/DB identity, inventory, booking tạo tay). Lỗi Flyway/DB → `make clean`.
- Kafka `localhost:19092`, Redis `localhost:6379`, inventory `:8083`, booking `:8084`.
- Kịch bản thử booking: `bash <scratchpad>/booking-e2e.sh` (mất khi reboot – nội dung trong PR ORD-1c).
- User test local: `admin@local.test` / `Password123` (ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5; gh pr list -R MarkusKeithThomas/pro-ticket-workload
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
