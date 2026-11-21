# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng – ROLLOUT inventory + booking (thứ tự, xem `docs/tasks/catalog.md` mục ROLLOUT)
1. Tạo 2 secret AWS: `unbutu/prod/inventory-service`, `unbutu/prod/booking-service` (key `DB_PASSWORD`).
2. Merge + apply **pro-ticket-workload #5** → Claude mở PR ptw từ nhánh `feat/booking-service` (đã push) → merge + apply.
3. Claude tạo DB `inventory`, `booking` trên prod (`docs/runbooks/add-service-database.md`).
4. unbutu-platform **#33** (draft, inventory) → Ready → merge → Approve deploy.
5. Claude đưa nhánh **`feat/ord-1c-booking`** (đã push, CHƯA mở PR – nối sau #33) lên `main`: nhánh mới từ main + cherry-pick → PR → merge → deploy.
   **Bài học #32**: PR xếp chồng merge vào nhánh cũ sau khi PR dưới đã squash → mất khỏi main. Chỉ mở PR khi base đã là `main`.

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
