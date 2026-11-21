# unbutu-platform – Hệ thống đặt vé sự kiện/phim (microservice)

Dự án luyện kỹ năng backend senior trong môi trường giống công ty:
microservice Spring Boot, CI/CD có duyệt deploy, observability (SigNoz/OpenTelemetry), phân quyền, API Gateway.
Chạy thật trên 1 server nhà (`unbutu`, Docker Compose) và public qua Tailscale Funnel.

- Kiến trúc, hạ tầng, lộ trình: [ARCHITECTURE.md](ARCHITECTURE.md)
- Quyết định kiến trúc: [docs/adr/](docs/adr/) · Runbook: [docs/runbooks/](docs/runbooks/) · Nhật ký setup: [docs/setup-steps.md](docs/setup-steps.md)

## Trạng thái (2026-11-16)

| Thành phần | Trạng thái |
|---|---|
| `api-gateway` | Chạy prod. Có routing, `X-Request-Id`, timeout. Chưa có access log, JWT, CORS |
| `catalog-service` | Chạy prod: sự kiện + hạng vé (đọc công khai, ghi cần ADMIN) |
| `identity`, `inventory`, `booking`, `payment`, `notification` | Mới có khung thư mục + README, chưa có code |
| `libs/platform-starter` | `X-Request-Id` → MDC, access log, `RestClient` truyền `X-Request-Id` (catalog dùng) |
| `libs/event-contracts` | Mới có khung, chưa có code |
| `frontend/` | React + Vite (nginx): đăng nhập/đăng ký, Google login, quản trị sự kiện + hạng vé |
| CI/CD | Xong: PR → CI → merge `main` → Release (image theo digest) → Deploy **cần Approve**, tự rollback |
| Observability | SigNoz (ADR-0006): log JSON ECS + trace + metrics (OpenTelemetry Java agent, chỉ prod) + metrics host/container, dashboard dạng code |

**API đang có** (public: `https://unbutu.tail3c11f4.ts.net`, local: `http://localhost:8080`):

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/catalog/ping` | Kiểm tra sống (smoke test của deploy) |
| GET | `/api/catalog/events` | Danh sách sự kiện (prod rỗng; local có dữ liệu mẫu) |
| GET | `/api/catalog/events/{id}` | Chi tiết sự kiện |
| GET | `/api/catalog/events/{id}/ticket-types` | Hạng vé đang bán / tạm dừng của sự kiện (không lộ bản nháp) |
| POST · PUT · DELETE | `/api/catalog/admin/events[/{id}]` | Tạo / sửa / xoá sự kiện – **ADMIN** (xoá được khi chưa có hạng vé) |
| GET · POST · PUT · DELETE | `/api/catalog/admin/ticket-types[/{id}]` | CRUD hạng vé – **ADMIN**. Đã từng mở bán → không xoá hẳn / không về Nháp (409) |
| POST | `/api/identity/register` · `/login` · `/refresh` · `/logout` | Đăng ký / đăng nhập email+mật khẩu; refresh token trong cookie HttpOnly |
| GET | `/api/identity/me` | Hồ sơ (cần `Authorization: Bearer`) |
| GET | `/api/identity/providers` | Cách đăng nhập đang bật (`google: true/false`) |
| GET | `/api/identity/oauth2/authorization/google` | Bắt đầu login Google (redirect) |
| GET | `/admin/events`, `/admin/ticket-types` | UI quản trị (chỉ ADMIN) |
| GET | `/`, `/login`, `/register` | UI (container `frontend`, gateway route mọi path không phải `/api/**`) |

## Công nghệ
Java 21 · Spring Boot 4.1 · Spring Cloud Gateway 5 (WebFlux) · PostgreSQL 17 + Flyway · Gradle Kotlin DSL ·
Docker Compose · GitHub Actions + GHCR · Tailscale (Funnel, OIDC) · AWS SSM/Secrets Manager (cấu hình prod) · SigNoz.
Dự kiến: Redis, RabbitMQ (Saga/Outbox), OpenTelemetry.

## Cấu trúc repo
```
services/<tên>/          # mỗi service = 1 module Gradle = 1 image = 1 container (package com.unbutu.ticket.<tên>)
libs/                    # platform-starter (requestId, access log), event-contracts – chưa có code
deploy/
  compose.local.yml      # chạy ở máy dev (Makefile dùng)
  compose.app.yml        # prod – chỉ deploy.sh dùng, không chạy tay
  compose.observability.yml + signoz/   # SigNoz trên server – admin dựng tay (docs/runbooks/signoz.md)
  docker/service.Dockerfile · postgres/init/ · scripts/deploy.sh
docs/                    # adr/, runbooks/, postmortems/, diagrams/, setup-steps.md
.github/workflows/       # ci.yml · release.yml · deploy.yml
```

## Chạy local

**Cần có:** Java 21, Docker Desktop **đang chạy**, `make`, IntelliJ (để debug).

`compose.local.yml` hiện chạy **`postgres`, `catalog-service`, `api-gateway`**. Service mới sẽ được thêm vào khi có code.
Port cố định trên máy, giống nhau dù chạy trong container hay IntelliJ (đặt trước cho cả service chưa có code):

| Service | Port | | Service | Port |
|---|---|---|---|---|
| api-gateway | 8080 | | booking-service | 8084 *(chưa có)* |
| identity-service | 8081 | | payment-service | 8085 *(chưa có)* |
| catalog-service | 8082 | | notification-service | 8086 *(chưa có)* |
| inventory-service | 8083 *(chưa có)* | | postgres | 5432 |
| frontend (nginx) | 8090 | | | |

### Lệnh `make` (gõ `make` để xem danh sách)
```bash
make up                          # build jar + image, chạy tất cả
make up s=catalog-service        # chỉ 1 service (+ hạ tầng nó cần)
make restart s=catalog-service   # build lại + khởi động lại sau khi sửa code
make logs s=catalog-service      # xem log · make ps: trạng thái
make down                        # tắt (giữ dữ liệu DB) · make clean: tắt + XOÁ dữ liệu DB
make build                       # build + toàn bộ test (như CI) · make test: chỉ test · make jar: jar, bỏ test
curl localhost:8080/api/catalog/events
```

### Debug 1 service trong IntelliJ (các service khác vẫn chạy trong container)
1. `make ide s=catalog-service`: chạy mọi thứ trong container **trừ** `catalog-service`. Gateway được trỏ sang
   `host.docker.internal:8082`, tức là bản đang chạy trong IntelliJ.
2. IntelliJ: chạy **Debug** `CatalogServiceApplication` với biến môi trường `SPRING_PROFILES_ACTIVE=local`.
   Lần đầu cần đặt ở Run → Edit Configurations → Environment variables. Profile `local` (`application-local.yml`)
   dùng port cố định, DB `localhost:5432`, và gọi service khác ở `localhost:<port>`.
3. Đặt breakpoint, rồi gọi qua gateway (`curl localhost:8080/api/catalog/...`) hoặc gọi thẳng `localhost:8082`.
   Sửa code thì chỉ cần Rerun trong IntelliJ, không phải build image.
4. Xong: `make up` để đưa service về lại container.

### Database & dữ liệu mẫu
- `localhost:5432`. Mỗi service có 1 database + 1 user riêng, ví dụ `catalog_db` / user `catalog` / mật khẩu `catalog`
  (chỉ ở local). Bảng do Flyway của từng service tạo khi khởi động: `services/<svc>/src/main/resources/db/migration/V<n>__<mô tả>.sql`.
- Dữ liệu mẫu chỉ có ở local và integration test, ví dụ `services/catalog-service/src/main/resources/db/seed-local/`
  (Flyway nạp khi chạy profile `local`). Prod khởi động với bảng rỗng.
- DB local tạo trước 2026-11-15 mà báo lỗi checksum Flyway thì chạy `make clean && make up` một lần.

## Quy trình làm việc
- Mỗi việc: `git checkout main && git pull --ff-only`, tạo nhánh mới (`feat/…`, `fix/…`, `docs/…`), mở PR, rồi merge.
  Commit theo Conventional Commits.
- PR chỉ sửa tài liệu (`*.md`, `docs/**`) hoặc cấu hình SigNoz/local thì không chạy CI/Release/Deploy (`paths-ignore`).

## Deploy prod
Không deploy tay. Merge `main` → CI → Release (build image **một lần**, deploy theo digest) → **Approve** trong tab Actions
(environment `production`) → `deploy.sh` trên server, có smoke test và tự rollback khi lỗi.
Rollback tay: Actions → Deploy → Run workflow với SHA cũ.
Cấu hình/secret prod: khai báo trong repo private `pro-ticket-workload`, value chỉ nằm trên AWS (ADR-0004). Repo này không chứa secret.

## Observability
SigNoz chỉ mở trong tailnet: `https://unbutu.tail3c11f4.ts.net:8443`. Prod: log JSON ECS (có `trace_id`, `requestId`),
trace + metrics qua OpenTelemetry Java agent, metrics host/container; dashboard trong `deploy/signoz/dashboards/`.
Local **không** bật agent và log dạng text cho dễ đọc. Vận hành: [docs/runbooks/signoz.md](docs/runbooks/signoz.md).
