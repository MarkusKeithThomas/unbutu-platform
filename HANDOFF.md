# HANDOFF – việc cần làm ngay

> Chỉ ghi việc tiếp theo. Cuối mỗi phiên **viết đè** file này. Không chép task đã xong.
> Chi tiết task: `docs/tasks/<mảng>.md` (chỉ đọc mục "Đang mở"). Cập nhật: 2026-11-21.

## Đang chờ người dùng
- Review + merge **PR #28** (CRUD sự kiện + hạng vé, màn `/admin/*`) → Approve deploy.
- Sau đó PR **khách xem/chọn vé** (nhánh `feat/catalog-client-browse`, base = nhánh #28): khi #28 merge (squash) → Claude `git rebase --onto origin/main feat/catalog-admin-crud feat/catalog-client-browse`, force-push, đổi base PR sang `main`.

## Làm ngay sau khi deploy
1. **CAT-1** (`docs/tasks/catalog.md`): cấp ADMIN cho tài khoản quản trị trên prod
   (`UPDATE users SET roles='CUSTOMER,ADMIN' WHERE email='…'` trong `identity_db`) → đăng nhập lại → thử `/admin/ticket-types`.
2. Việc tiếp theo: **ORD-1 – giữ vé 10′** (inventory + Redis Lua + booking) – xem `docs/tasks/catalog.md`. Mọi code: tự kiểm mở rộng 50k rps.

## Ghi chú local
- DB local cũ: đã sửa checksum V1 của `catalog_db` + tạo tay user/DB `identity`. Gặp lại lỗi Flyway/DB thiếu → `make clean` (xoá hết dữ liệu local).
- User test local: `admin@local.test` / `Password123` (đã là ADMIN).

## Kiểm tra nhanh đầu phiên
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -2 /opt/unbutu-platform/prod/deploy-history.log; docker ps --format "{{.Names}} {{.Status}}"'
```
