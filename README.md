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
| `inventory-service` | Tồn kho + giữ vé: Redis Lua (oversell = 0) + sổ cái Postgres, nghe `catalog.event.v1` |
| `booking-service` | Giữ vé 10′ (idempotent, 4 vé/tài khoản/sự kiện), huỷ, tự hết hạn; bảng giá từ `catalog.event.v1`; phát `booking.event.v1` (`booking.confirmed`, Outbox) |
| `payment-service` | Thanh toán giao thức VNPay 2.1.0 – **cổng giả lập** (IPN là nguồn sự thật, Outbox `payment.result.v1`, hoàn tiền) |
| `loadtest-service` | Nút bấm load test (`/admin/loadtest`, chỉ ADMIN): chạy k6 trong container, số liệu trực tiếp, lịch sử – `docs/runbooks/loadtest.md` |
| `notification-service` | **Vé điện tử QR** (JWS ES256) khi đơn `CONFIRMED` – nghe `booking.event.v1`; `/api/tickets/**` (ADR-0009). Sau: email |
| `libs/outbox` | Transactional Outbox dùng chung (catalog, payment) |
| `libs/platform-starter` | `X-Request-Id` → MDC, access log, `RestClient` truyền `X-Request-Id` (catalog dùng) |
| `libs/event-contracts` | Schema event (`EventEnvelope`, `CatalogEvents`, `PaymentEvents`, `BookingEvents`) – topic `catalog.event.v1`, `payment.result.v1`, `booking.event.v1` |
| `frontend/` | React + Vite (nginx): đăng nhập/đăng ký, Google login, quản trị sự kiện + hạng vé |
| CI/CD | Xong: PR → CI → merge `main` → Release (image theo digest) → Deploy **cần Approve**, tự rollback |
| Observability | SigNoz (ADR-0006): log JSON ECS + trace + metrics (OpenTelemetry Java agent, chỉ prod) + metrics host/container, dashboard dạng code |

**API đang có** (public: `https://unbutu.tail3c11f4.ts.net`, local: `http://localhost:8080`):

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/catalog/ping` | Kiểm tra sống (smoke test của deploy) |
| GET | `/api/catalog/events?page=&size=` | Khách: sự kiện sắp diễn ra đang mở bán + `priceFrom`, `{items, page, size, hasNext}` (size ≤ 50, không COUNT). `Cache-Control: public, max-age=30` |
| GET | `/api/catalog/events/{id}` | Chi tiết sự kiện (cache 30 s) |
| GET | `/api/catalog/events/{id}/ticket-types` | Hạng vé đang bán / tạm dừng của sự kiện, có mô tả (không lộ bản nháp, cache 30 s) |
| GET · POST · PUT · DELETE | `/api/catalog/admin/events[/{id}]` | Danh sách đầy đủ / tạo / sửa / xoá sự kiện – **ADMIN** (xoá được khi chưa có hạng vé) |
| GET · POST · PUT · DELETE | `/api/catalog/admin/ticket-types[/{id}]` | CRUD hạng vé – **ADMIN**. Đã từng mở bán → không xoá hẳn / không về Nháp (409) |
| GET | `/api/inventory/events/{id}/availability` | "Còn x / y" realtime của từng hạng vé (`Cache-Control: public, max-age=1`) |
| POST · GET · DELETE | `/api/bookings` (+ `Idempotency-Key`), `/api/bookings/{id}`, `/api/bookings/me` | Giữ vé 10′ / xem đơn / huỷ – cần đăng nhập |
| POST | `/api/bookings/{id}/checkout` | Sang cổng thanh toán (`{method, buyer}` → `paymentUrl`) |
| GET | `/api/payments/vnpay/ipn`, `/api/payments/vnpay/return`, `/api/payments/mock/pay` | IPN (server→server) / trình duyệt quay về / trang ngân hàng giả lập |
| GET · PUT · DELETE | `/api/bookings/admin/limit-bypass[/{eventId}]` | Cờ load test: bỏ giới hạn 4 vé cho 1 sự kiện, có hạn – **ADMIN** |
| GET · POST | `/api/loadtest/config`, `/api/loadtest/runs[/{id}]`, `/api/loadtest/runs/current`, `/api/loadtest/runs/{id}/stop` | Load test – **ADMIN** |
| POST | `/api/identity/register` · `/login` · `/refresh` · `/logout` | Đăng ký / đăng nhập email+mật khẩu; refresh token trong cookie HttpOnly |
| GET | `/api/identity/me` | Hồ sơ (cần `Authorization: Bearer`) |
| GET | `/api/identity/providers` | Cách đăng nhập đang bật (`google: true/false`) |
| GET | `/api/identity/oauth2/authorization/google` | Bắt đầu login Google (redirect) |
| GET | `/admin/events`, `/admin/ticket-types`, `/admin/loadtest` | UI quản trị (chỉ ADMIN) |
| GET | `/`, `/events/{id}`, `/checkout/{bookingId}`, `/orders/{bookingId}` | UI khách: sự kiện → chọn vé → bước 2 (giữ vé, người mua, thanh toán) → bước 3 kết quả |
| GET | `/login`, `/register` | UI (container `frontend`, gateway route mọi path không phải `/api/**`) |

## Công nghệ
Java 21 · Spring Boot 4.1 · Spring Cloud Gateway 5 (WebFlux) · PostgreSQL 17 + Flyway · Gradle Kotlin DSL ·
Docker Compose · GitHub Actions + GHCR · Tailscale (Funnel, OIDC) · AWS SSM/Secrets Manager (cấu hình prod) · SigNoz.
Redpanda (Kafka API) + Transactional Outbox (ADR-0007). Dự kiến: Redis (giữ vé, Lua).

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

`compose.local.yml` hiện chạy **`postgres`, `redpanda` (Kafka `localhost:19092`), `redis` (`localhost:6379`), `identity-service`, `catalog-service`, `inventory-service`, `booking-service`, `payment-service`, `notification-service`, `frontend`, `api-gateway`, `kafka-ui` (`localhost:8091`)**. Service mới sẽ được thêm vào khi có code.
Port cố định trên máy, giống nhau dù chạy trong container hay IntelliJ (đặt trước cho cả service chưa có code):

| Service | Port | | Service | Port |
|---|---|---|---|---|
| api-gateway | 8080 | | booking-service | 8084 |
| identity-service | 8081 | | payment-service | 8085 |
| catalog-service | 8082 | | notification-service | 8086 |
| inventory-service | 8083 | | postgres | 5432 |
| frontend (nginx) | 8090 | | kafka-ui | 8091 |
| loadtest-service | 8087 | | | |

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
