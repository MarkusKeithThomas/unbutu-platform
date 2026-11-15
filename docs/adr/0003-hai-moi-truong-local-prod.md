# ADR-0003: Chỉ 2 môi trường – local + prod, bỏ staging

- Trạng thái: Chấp nhận · 2026-11-15
- Thay thế một phần: ADR-0001 (bỏ compose project `staging`)

## Bối cảnh
Thiết kế ban đầu có staging + prod cùng chạy trên `unbutu` (merge `main` → staging tự động → promote digest lên prod cần duyệt).
`unbutu` là laptop 11 GiB RAM: prod (~3 GB) + staging (~3 GB) + ELK (~3 GB) + OS (~1 GB) ≈ 10 GB – máy không chịu nổi, nhất là khi thêm S1/S4.

## Quyết định
- 2 môi trường:
  - **local**: MacBook của người dùng – `deploy/compose.local.yml` + IntelliJ (hybrid).
  - **prod**: `unbutu`, chỉ thay đổi qua CI/CD.
- Luồng: PR → `ci.yml` (build + test + build thử image) → merge `main` → CI xanh → `release.yml` build image **một lần**, push `ghcr.io/<user>/<svc>:<sha>` → job deploy (GitHub Environment `production`, required reviewers) **chờ duyệt** → `deploy.sh prod <sha>` deploy theo digest → healthcheck, fail thì rollback về SHA cũ.
- Bỏ `promote.yml` và bước gắn tag cho digest từ staging.

## Hệ quả
- (+) Tiết kiệm ~3 GB RAM; pipeline đơn giản hơn.
- (+) Vẫn giữ: build một lần, deploy theo digest, prod cần duyệt, rollback bằng image cũ trên GHCR.
- (−) Không có nơi chạy thử bản mới trên hạ tầng giống prod trước khi lên prod → phụ thuộc vào test (Testcontainers), bước duyệt, healthcheck + rollback.
- (−) Role `developer` (§7) mất staging để thao tác → chỉ còn Kibana chỉ đọc; quyền thêm chốt khi làm §7.
