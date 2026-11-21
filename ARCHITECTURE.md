# Kiến trúc hệ thống – unbutu platform

> Tài liệu nguồn sự thật (source of truth) về hạ tầng và kiến trúc. Cập nhật file này mỗi khi hạ tầng hoặc quyết định kiến trúc thay đổi.
> Trạng thái: **Prod đang chạy (api-gateway + catalog-service + postgres) · CI/CD đầy đủ, deploy cần duyệt · Cấu hình/secret trên AWS · SigNoz đang chạy (project `observability`, UI `https://unbutu.tail3c11f4.ts.net:8443` chỉ tailnet) – đã thu log Docker của prod, chưa có trace/metrics** (cập nhật 2026-11-16)
> Các bước đã làm theo thứ tự: [docs/setup-steps.md](docs/setup-steps.md)

---

## 1. Tổng quan

Hệ thống microservice Java Spring Boot, **mỗi container = 1 service**, chạy trên **một máy chủ tự host tại nhà** (`unbutu`). Các service giao tiếp nội bộ qua DNS theo tên service (giống Kubernetes Service/ClusterIP). Chỉ duy nhất API Gateway được mở ra Internet qua **Tailscale Funnel**. Frontend host trên **Vercel**.

```
User (trình duyệt)
  │ https://<app>.vercel.app
  ▼
Vercel (UI tĩnh + CDN)
  │ rewrite /api/*  →  https://unbutu.tail3c11f4.ts.net/api/*
  ▼
Tailscale Funnel relay (IP công khai của Tailscale, chỉ chuyển tiếp TLS, không giải mã)
  │ đường hầm WireGuard (unbutu chủ động mở ra ngoài → không cần mở port router)
  ▼
unbutu: tailscaled giải mã TLS → proxy http://127.0.0.1:8080
  ▼
┌──────────────── Docker network nội bộ "prod_default" ───────────┐
│ api-gateway (Spring Cloud Gateway) :8080  ← DUY NHẤT publish port │
│   /api/catalog/**  → http://catalog-service:8080   (đang chạy)    │
│   /api/<domain>/** → http://<domain>-service:8080  (thêm dần)     │
│ *-service  (KHÔNG publish port – chỉ gọi được từ trong mạng)      │
│ postgres   (mỗi service 1 database riêng)                         │
└───────────────────────────────────────────────────────────────────┘
```

---

## 2. Hạ tầng hiện tại (đã cấu hình & đã kiểm chứng)

### 2.1 Máy chủ `unbutu`
| Mục | Giá trị |
|---|---|
| Phần cứng | HP ProBook 450 G2 (laptop, 2015), 4 vCPU, 11 GiB RAM, SSD 512 GB (LVM đã mở rộng hết: / = 466 GB), swap 4 GB |
| OS | Ubuntu 24.04.5 LTS, x86_64 |
| User | `chauhoaithuong` (sudo không cần mật khẩu: `/etc/sudoers.d/chauhoaithuong`) |
| Mạng | Wifi `wlo1`, IP LAN DHCP (từng là .219, hiện .167) → **không dùng IP LAN** |
| Pin | **Hỏng (0%)** – chạy hoàn toàn bằng sạc; cúp điện = tắt ngay |
| Ổ đĩa | Không mã hóa (boot không cần nhập gì) |

### 2.2 Đã cấu hình trên server
- **Docker** 29.9.3 + **Compose** v5.7.1, tự khởi động; user đã ở nhóm `docker`.
- **Tailscale** 1.104.7, tài khoản tailnet của chủ repo.
  - Tailscale IP: `100.67.135.86` · MagicDNS: `unbutu.tail3c11f4.ts.net`
  - **Funnel bật**: `https://unbutu.tail3c11f4.ts.net` (443) → `http://127.0.0.1:8080` (cấu hình lưu trong tailscaled, tồn tại qua reboot).
  - **Key expiry: đã tắt** (node key không hết hạn).
  - **Tailscale SSH: KHÔNG bật** (chế độ "check" mặc định bắt xác thực trình duyệt → phá CI/automation). Dùng OpenSSH + key.
- **Firewall ufw**: default deny incoming. Cho phép: mọi thứ trên `tailscale0`; `22/tcp` từ `192.168.1.0/24` (dự phòng LAN); `41641/udp` (Tailscale direct); `5353/udp` từ LAN (mDNS cho `unbutu.local`).
- **Không sleep**: lid switch = ignore (logind.conf.d/server.conf), sleep/suspend/hibernate targets bị `mask`, wifi power save off.
- **Tự boot sau mất điện**: `GRUB_RECORDFAIL_TIMEOUT=5`. BIOS "Power On When AC Detected" – **cần người dùng bật tay (F10)**, chưa xác nhận.
- **unattended-upgrades** bật (cập nhật bảo mật tự động).
- **Docker log rotation**: `/etc/docker/daemon.json` → json-file, max-size 10m × 3 file/container.
- **sshd hardening** (`/etc/ssh/sshd_config.d/10-hardening.conf`): PermitRootLogin no, X11Forwarding no, MaxAuthTries 3. PasswordAuthentication vẫn **yes** (chỉ truy cập được qua tailnet/LAN).
- **smartmontools** cài sẵn: `sudo smartctl -H -A /dev/sda` (SSD Transcend 512 GB, SMART PASSED lúc setup).
- Timezone hệ thống: UTC.
- Đã kiểm thử reboot (2026-11-15): docker, tailscaled, ssh, ufw, container, Funnel tự lên lại; Funnel cần ~1 phút sau boot.
- Container thử `hello` đã xoá; `127.0.0.1:8080` giờ là `prod-api-gateway-1`.
- **Deploy** (§6.2): `/opt/unbutu-platform/bin/deploy.sh` (root 755, admin cài tay), `/opt/unbutu-platform/prod/` (compose, `env/` 700, `releases/`, `images.current.env`, `deploy-history.log`).
- User **`deploy`** (uid 111, không thuộc nhóm docker): chỉ SSH bằng key của CI có forced command; sudoers `/etc/sudoers.d/unbutu-deploy` chỉ cho `deploy.sh -`.
- **AWS CLI v2** (`/usr/local/bin/aws`, kiểm chữ ký GPG) + `/root/.aws` (600) = IAM user `unbutu-server` chỉ đọc (ADR-0004). Thêm gói `unzip`, `jq` có sẵn.

### 2.3 Truy cập từ máy dev (MacBook, `chaus-macbook-pro`, Tailscale IP `100.84.174.47`)
`~/.ssh/config`:
- `ssh unbutu` → `unbutu.tail3c11f4.ts.net` (qua Tailscale, dùng mọi mạng) – key `~/.ssh/id_ed25519_unbutu` (không passphrase).
- `ssh unbutu-lan` → `unbutu.local` (mDNS, chỉ khi cùng LAN).
- `~/.ssh/id_rsa` cũ có passphrase không nhớ → không dùng cho server này.

Thiết bị khác muốn SSH: cài Tailscale + đăng nhập cùng tài khoản (hoặc được share node), rồi SSH bằng mật khẩu/key. Người dùng cuối **không** cần Tailscale – chỉ dùng link Vercel.

---

## 3. Mục tiêu dự án

Dự án là **môi trường luyện kỹ năng backend/vận hành giống môi trường công ty**, không tốn thời gian vào Kubernetes. **Quyết định: Docker Compose** (không k3s/k8s).

| # | Mục tiêu | Thực hiện ở | Mục |
|---|---|---|---|
| G1 | CI/CD thật: PR → test → merge `main` → build image → deploy prod (cần duyệt), rollback theo healthcheck | GitHub Actions + GHCR + Tailscale | §6 |
| G2 | Đóng vai người trace bug: tìm lỗi qua log/trace tập trung, không SSH | SigNoz (OpenTelemetry + ClickHouse), ADR-0006 | §5 |
| G3 | Làm việc với hệ thống **theo phân quyền** (dev không có quyền vào prod) | Linux user, Tailscale ACL, SigNoz role, GitHub Environments | §7 |
| G4 | Nghiên cứu & cài các system design bắt buộc ở **API Gateway** | Spring Cloud Gateway + Redis | §4 |
| G5 | **requestId/traceId xuyên suốt mọi service**, tra trên SigNoz | OpenTelemetry/OTLP (W3C `traceparent`) + `X-Request-Id` + ECS JSON log | §5 |
| G6 | Lộ trình kỹ năng senior: metrics, incident, migration, saga, backup, load test, chaos, security | Xem checklist | §8 |

---

## 4. Kiến trúc ứng dụng

### 4.1 Sơ đồ (trên unbutu)
```
Internet ─▶ Funnel :443 ─▶ 127.0.0.1:8080 (prod gateway)
Tailnet  ─▶ tailscale serve :8443 ─▶ 127.0.0.1:3301 (SigNoz UI)     ← chỉ trong tailnet, không Funnel
Tailnet  ─▶ tailscale serve :9443 ─▶ 127.0.0.1:8091 (kafka-ui, chỉ đọc – ADR-0008)
(Không có staging trên server – môi trường thứ 2 là local trên MacBook, ADR-0003.)

┌─ project "prod" (network prod_default) ─┐
│ api-gateway ─┬─▶ identity / catalog     │
│   + redis    ├─▶ booking ─▶ inventory   │
│              └─▶ payment                │
│ rabbitmq ─▶ notification                │
│ postgres (DB riêng/service)             │
└──────────────┬──────────────────────────┘
               │ stdout JSON log (ECS) + OTLP (trace, metrics)
               ▼
        ┌─ project "observability" (ADR-0006) ─────────────────────┐
        │ otel-collector (đọc log Docker + nhận OTLP :4317/:4318)   │
        │   ─▶ clickhouse ◀─ signoz (UI, query, alert)             │
        └──────────────────────────────────────────────────────────┘
```

### 4.2 Thành phần – hệ thống đặt vé sự kiện / phim
| Service | Vai trò | Dữ liệu | Ghi chú |
|---|---|---|---|
| `api-gateway` | Spring Cloud Gateway (WebFlux) – cổng vào duy nhất | Redis | xem §4.4 |
| `identity-service` | Đăng ký/đăng nhập, phát JWT (RS256 + JWKS), role CUSTOMER/ORGANIZER/ADMIN | `identity_db` | |
| `catalog-service` | Sự kiện/phim, địa điểm, phòng, suất diễn, loại vé & giá | `catalog_db` | đọc nhiều → cache |
| `inventory-service` | Sơ đồ ghế theo suất, **giữ ghế có TTL**, xác nhận/nhả ghế, chống double-booking | `inventory_db` + Redis | bài toán concurrency chính |
| `booking-service` | Đơn đặt vé, **điều phối Saga**, Outbox, idempotency key, phát hành vé QR | `booking_db` | |
| `payment-service` | Cổng thanh toán **giả lập** (cấu hình tỉ lệ lỗi/độ trễ), webhook, hoàn tiền | `payment_db` | dùng để luyện sự cố |
| `notification-service` | **Vé điện tử QR** (ADR-0009) khi `booking.confirmed`; sau: email/Telegram | `notification_db` | consumer idempotent, vé = dữ liệu gốc |
| `postgres` | PostgreSQL 17, 1 database + user riêng/service | | nội bộ |
| `redis` | Rate limit (gateway), seat hold TTL (inventory) | | nội bộ |
| `rabbitmq` | Event bất đồng bộ giữa service | | nội bộ |
| `signoz` / `clickhouse` / `otel-collector` | Log, metrics, trace, alert (ADR-0006) | | project `observability` |
| `kafka-ui` | Xem topic/message/consumer lag/DLT của Redpanda – **chỉ đọc** (ADR-0008) | | project `prod`, tailnet `:9443` |
| `frontend` | UI trên Vercel – framework chưa chốt (đề xuất React + Vite) | | không chạy trên unbutu |

Thư viện dùng chung: `libs/platform-starter` (requestId, ECS log, Problem Details, RestClient), `libs/event-contracts` (schema event).
Chi tiết từng service: `services/<tên>/README.md`.

### 4.2.1 Luồng đặt vé (Saga do booking-service điều phối)
```
Client ─POST /api/bookings (Idempotency-Key)─▶ gateway ─▶ booking-service
  1. booking → inventory: hold ghế (TTL 10 phút)          [REST đồng bộ]
  2. booking: lưu booking PENDING + outbox "booking.created"   (1 transaction)
  3. payment nghe booking.created → tạo payment → mock gateway
  4a. payment.succeeded → booking: CONFIRMED → inventory: SOLD → "booking.confirmed" → notification gửi vé
  4b. payment.failed / hết TTL → booking: CANCELLED/EXPIRED → inventory: nhả ghế → notification báo hủy
```
Mọi bước mang cùng `traceId` + `requestId` (REST header và message header).

### 4.3 Ánh xạ Kubernetes ↔ Docker Compose
| Kubernetes | Ở đây |
|---|---|
| Namespace | Compose project riêng: `prod`, `observability` |
| Deployment/Pod | 1 `service` trong compose |
| Service DNS / ClusterIP | Tên service trên network nội bộ (`http://user-service:8080`), không khai báo `ports:` |
| Ingress | `api-gateway` + Tailscale Funnel |
| replicas | `deploy.replicas` (Docker DNS round-robin) |
| resources.limits | `deploy.resources.limits` |
| readiness/liveness | `healthcheck` (Actuator) + `depends_on: condition: service_healthy` |
| ConfigMap/Secret (+ External Secrets) | `env_file: env/<svc>.env` do `deploy.sh` sinh từ AWS SSM (bản đồ) + Secrets Manager (value) – ADR-0004 |
| PersistentVolume | named volume |
| RBAC | Linux user + Tailscale ACL + SigNoz role (§7) |

### 4.4 API Gateway – các hạng mục system design (nghiên cứu & cài dần)
| Ưu tiên | Hạng mục | Cách làm (Spring Cloud Gateway) |
|---|---|---|
| P0 | Routing theo path `/api/<domain>/**` | `RouteLocator` / `spring.cloud.gateway.routes` |
| P0 | **Request ID / correlation** | Global filter: nhận hoặc sinh `X-Request-Id`, đẩy xuống downstream, trả về response header; tracing tự sinh `traceparent` |
| P0 | Access log có cấu trúc | **Đã có** (`AccessLogWebFilter`): method, path, status, latency, client IP (XFF qua Funnel), requestId, trace_id. Còn userId (sau JWT) |
| P0 | Xác thực JWT tại biên | Spring Security OAuth2 Resource Server; gateway chuyển `X-User-Id`/`X-User-Roles` xuống service |
| P0 | Timeout | `httpclient.connect-timeout`, `response-timeout` theo route |
| P0 | CORS | `globalcors` (dù đi qua Vercel rewrite vẫn cần khi gọi thẳng ts.net) |
| P1 | Rate limiting | `RequestRateLimiter` + Redis (token bucket), key theo user/IP |
| P1 | Circuit breaker + fallback | Resilience4j `CircuitBreaker` filter, `fallbackUri` |
| P1 | Retry (chỉ GET/idempotent) | `Retry` filter + backoff |
| P1 | Giới hạn kích thước request | `RequestSize` filter |
| P1 | Chuẩn hoá lỗi | Global error handler → JSON thống nhất `{code, message, requestId}` |
| P2 | Phân quyền theo route (RBAC) | Security rule theo path + role |
| P2 | API versioning | `/api/v1/...`, `/api/v2/...` |
| P2 | Canary / weighted routing | `Weight` predicate (vd. 90% v1 / 10% v2) |
| P2 | Response cache | `LocalResponseCache` filter hoặc Redis |
| P2 | IP allow/deny, API key cho đối tác | Custom predicate/filter |
| P2 | Idempotency key cho POST | Header `Idempotency-Key` + Redis |
| P2 | Metrics | Actuator + Micrometer → OTLP → SigNoz (làm cùng S1) |
| P3 | Aggregation / BFF | Endpoint gateway gộp nhiều service |

### 4.5 Quy ước bắt buộc
- API công khai dưới `/api/<domain>/**`; service gọi nhau bằng `http://<service-name>:8080`, URL từ biến môi trường.
- **Không bao giờ** publish port ra `0.0.0.0` – Docker bỏ qua ufw. Prod gateway `127.0.0.1:8080`; cổng chỉ-tailnet bind vào IP Tailscale `100.67.135.86`.
- Gọi HTTP giữa service **chỉ** dùng `RestClient.Builder`/`WebClient.Builder` do Spring inject (để tracing tự propagate header). Không `new RestTemplate()`.
- Log ra **stdout**, định dạng JSON ECS (`logging.structured.format.console=ecs`), mỗi dòng có `trace.id`, `span.id`, `requestId`, `service.name`, `service.environment`. Không log password/token/PII.
- Mỗi service có Actuator + `healthcheck`; `depends_on` chỉ trỏ tới hạ tầng (postgres, rabbitmq, redis), không trỏ tới service khác (service phải chịu được lúc service khác chưa sẵn sàng).
- Mỗi service có `application-local.yml` (port cố định, `localhost`) và URL service khác đặt qua thuộc tính `services.<tên>.url` (ghi đè bằng biến `<TÊN>_SERVICE_URL`). JVM `-XX:MaxRAMPercentage=75`, `mem_limit` ~512m.
- Database-per-service trong 1 container Postgres: mỗi service 1 database + user riêng, Flyway riêng; không đọc bảng của service khác.
- Cấu hình + secret prod: khai báo trong repo private `pro-ticket-workload` (`prod/services/<svc>/env.yaml`: `env` + đường dẫn `secrets`), value chỉ nằm trong AWS Secrets Manager (nhập trên Console). Không có `.env` gõ tay, không có value trong git hay Terraform state (ADR-0004).
- Link `ts.net` công khai → bảo mật bằng JWT tại gateway, không dựa vào giấu URL.

### 4.6 Ngân sách RAM (11 GiB)
Chỉ 1 môi trường trên server (`prod`) – không có staging vì laptop không đủ RAM (ADR-0003).

| Nhóm | Ước tính |
|---|---|
| prod: 7 service Spring Boot (~350–450 MB) + postgres + redis (~128 MB) + redpanda (limit 1,25 GB, đo ~100 MB) | ~4,5 GB |
| observability: SigNoz – ClickHouse (limit ~2 GB) + OTel Collector + SigNoz UI/query (ADR-0006) | ~3 GB |
| OS + Docker + Tailscale | ~1 GB |
| **Tổng** | **~8 GB** (đo 2026-11-21: đang dùng 3,2 GB, còn 8,6 GB available) – còn ~3 GB dư cho S4 (§8) và đỉnh tải (S1 đã nằm trong SigNoz) và đỉnh tải; vẫn cân nhắc `-XX:+UseSerialGC`, giới hạn thread pool |

---

## 5. Logging, tracing & metrics (G2, G5) – SigNoz (ADR-0006)

### 5.1 Luồng dữ liệu
```
log:    service (Logback, ECS JSON → stdout) → Docker json-file → otel-collector (đọc log Docker, gắn tên container)
trace:  service (OTel Java agent) ──OTLP──▶ otel-collector      (network telemetry, http://otel-collector:4318)
metric: service (OTel Java agent: JVM, HTTP, JDBC) ──OTLP──▶ otel-collector
          → clickhouse (TTL 15 ngày cho log/trace/metrics) → SigNoz UI: Logs / Traces / Dashboards / Alerts
```
Log vẫn ra stdout nên `docker logs` vẫn đọc được khi SigNoz chết. Đo đạc bằng OpenTelemetry Java agent, chỉ bật ở prod (ADR-0006 §"Đo đạc service").

### 5.2 requestId xuyên service
1. Request vào gateway: nếu có `X-Request-Id` thì dùng, không thì sinh UUID. Micrometer Tracing đồng thời tạo `traceId` và header W3C `traceparent`.
2. Gateway forward kèm `X-Request-Id` + `traceparent` → service A.
3. Service A: tracing đọc `traceparent` → cùng `traceId`; filter đọc `X-Request-Id` vào **MDC** + baggage để tự đi tiếp.
4. A gọi B bằng `RestClient` (inject) → header được propagate tự động.
5. Mọi dòng log có `trace.id` + `requestId`. Response trả `X-Request-Id` cho client; lỗi JSON cũng chứa `requestId`.
6. (Sau này có RabbitMQ/Kafka) propagate header vào message.

### 5.3 Quy trình trace bug (vai trò dev)
User báo lỗi + `requestId` (hiện trên màn hình lỗi FE) → SigNoz Logs → lọc `requestId = <id>` → lấy `trace.id` → mở trace waterfall (span nào chậm/lỗi) + log liên kết theo thời gian ở gateway → booking → inventory → tìm `log.level : ERROR` + stacktrace → xác định service/commit (`service.version` = git SHA của image).

---

## 6. CI/CD (G1)

### 6.1 Repo (monorepo, Gradle Kotlin DSL – đã tạo khung)
```
unbutu-platform/
├── CLAUDE.md · ARCHITECTURE.md · README.md
├── settings.gradle.kts · gradlew · gradle/wrapper/     # Gradle 9.9.1, Java 21
├── gradle/libs.versions.toml · buildSrc/ (convention plugin) · lombok.config · Makefile
├── .editorconfig · .gitignore
├── libs/
│   ├── platform-starter/       # requestId, ECS log, Problem Details, RestClient, outbox
│   └── event-contracts/        # schema event dùng chung
├── services/                   # package gốc com.unbutu.ticket.<tên>
│   ├── api-gateway/
│   ├── identity-service/
│   ├── catalog-service/
│   ├── inventory-service/
│   ├── booking-service/
│   ├── payment-service/
│   └── notification-service/
├── frontend/                   # vercel.json (rewrite /api/* → ts.net); framework chưa chốt
├── deploy/
│   ├── docker/service.Dockerfile  # 1 Dockerfile dùng chung (layered jar, không chạy root)
│   ├── postgres/init/          # tạo database cho từng service
│   ├── signoz/                 # config ClickHouse/Keeper/otel-collector cho SigNoz (ADR-0006)
│   ├── compose.observability.yml # SigNoz trên server – admin chạy tay (docs/runbooks/signoz.md)
│   ├── scripts/deploy.sh       # deploy prod trên server: env từ AWS + compose theo digest + rollback
│   ├── compose.app.yml         # prod trên server (image theo digest, env_file từ deploy.sh)
│   └── compose.local.yml
├── docs/{adr,postmortems,runbooks,diagrams}/ · docs/setup-steps.md
├── tools/{k6,chaos}/
└── .github/workflows/          # ci.yml · release.yml · deploy.yml
```
Đã có code: `api-gateway`, `catalog-service`. Chưa có: các service còn lại, `libs/*`, `frontend/`, `pull_request_template.md`. `deploy/compose.observability.yml` đã soạn, chưa dựng.

### 6.2 Pipeline
| Sự kiện | Việc làm | Môi trường |
|---|---|---|
| Mở/cập nhật PR | build, unit + integration test, build thử image (`ci.yml`, image bỏ đi) | – |
| Merge `main` | `ci.yml` chạy lại trên main; CI xanh → `release.yml`: build image **một lần** → push `ghcr.io/<user>/<svc>:<sha>` → job deploy (environment `production`) **chờ duyệt** → deploy theo digest | **prod** |
| PR/merge **chỉ** đụng `*.md`, `docs/**`, `deploy/signoz/**`, `deploy/compose.observability.yml`, `deploy/compose.local.yml` | `paths-ignore` trong `ci.yml` → không CI, không Release, không Deploy (không tốn build). Commit có lẫn file khác thì chạy đầy đủ | – |
| Bấm Run lại / chọn SHA cũ | deploy lại image `:<sha>` đã có trên GHCR (rollback tay), không build lại | **prod**, cần duyệt |

Chỉ 2 môi trường (ADR-0003): **local** (MacBook, `compose.local.yml` + IntelliJ) và **prod** (`unbutu`, qua CI/CD). Không có staging – laptop không đủ RAM.

**Nguyên tắc build một lần, deploy theo digest:**
- Image chỉ được build ở `release.yml`, một lần cho mỗi commit trên `main` có CI xanh, tag = git SHA đầy đủ.
- Deploy luôn tham chiếu **digest** (`ghcr.io/...@sha256:...`, ghi trong `images.env`), không tham chiếu tag di động như `latest` → thứ chạy ở prod đúng từng byte với image đã build từ SHA đó.
- Image đã build bị giữ trên GHCR → rollback = deploy lại digest của SHA cũ, không `docker build`.
- `ci.yml` không huỷ lần chạy trên `main` (`cancel-in-progress` chỉ bật cho PR) → mỗi SHA trên main đều có kết quả CI xanh/đỏ; release chỉ chạy cho SHA có CI xanh.
- Duyệt deploy: GitHub Environment `production` với required reviewers (miễn phí vì repo public); image đã push sẵn, chỉ bước deploy chờ bấm.

Deploy (`deploy.yml`, gọi từ `release.yml` hoặc chạy tay để deploy lại/rollback theo SHA): chờ **Approve** (environment `production`, chỉ nhánh `main`) → lấy artifact `images-<sha>` → `tailscale/github-action` (OIDC federated identity, `tag:ci`, không có secret) → `ssh deploy@100.67.135.86` (key trong secret `DEPLOY_SSH_KEY`; `authorized_keys`: `restrict,from="100.64.0.0/10",command="sudo -n /opt/unbutu-platform/bin/deploy.sh -"`; sudoers `/etc/sudoers.d/unbutu-deploy` chỉ cho đúng lệnh đó) → `deploy.sh` đọc `images.env` từ stdin:
1. kiểm tra `images.env` (chỉ image `ghcr.io/markuskeiththomas/*@sha256:…`);
2. sinh `env/<svc>.env` (root 600) từ SSM `/unbutu/prod/<svc>/env-map` + Secrets Manager – thiếu biến → dừng;
   + tải `compose.app.yml`, `postgres/init` đúng commit `GIT_SHA` từ GitHub (deploy.sh tự nó chỉ admin cài tay – CI không sửa được code chạy root);
3. `docker compose -p prod pull` (thử 3 lần – mạng nhà hay đứt) `&& up -d --wait` + smoke test `GET /api/catalog/ping` qua gateway;
4. lỗi → rollback về `images.current.env` (lần thành công trước; đã xảy ra thật với `4551749`). Lịch sử: `/opt/unbutu-platform/prod/deploy-history.log`.
5. workflow kiểm tra từ Internet: phân giải qua DNS public (`1.1.1.1`) – trong tailnet MagicDNS trả IP 100.x và ACL chỉ cho `tag:ci` → `:22`.

Rollback tay: **Actions → Deploy → Run workflow** với SHA cũ (40 ký tự, artifact còn trong 90 ngày) → Approve. Chi tiết: ADR-0005.

Cấu hình/secret (ADR-0004): repo private `pro-ticket-workload` (Terraform, PR → plan, merge → apply qua GitHub OIDC) giữ bản đồ biến từng service; value nhập trên AWS Console; server đọc bằng IAM user chỉ đọc `unbutu-server`.

Lưu ý: GitHub Environments có **required reviewers** cho repo **public** miễn phí; repo private cần gói trả phí → nếu private, thay bằng `workflow_dispatch` thủ công.

---

## 7. Phân quyền (G3) – mô phỏng "dev không vào được prod"

| Vai trò | Ai | Được làm | Không được |
|---|---|---|---|
| **admin** | `chauhoaithuong` (máy MacBook) | Mọi thứ: SSH sudo, cấu hình server | – |
| **deployer** | GitHub Actions (`tag:ci`) – **đã làm** | SSH user `deploy`, chỉ chạy `sudo -n deploy.sh -` (forced command + sudoers); Tailscale ACL chỉ `100.67.135.86:22` | shell tự do, sudo lệnh khác, đọc secret prod |
| **developer** | Tài khoản Tailscale thứ 2 / thiết bị thứ 2 (đóng vai nhân viên) | SigNoz (role Viewer) – trace bug qua log/trace, không SSH. Quyền thêm (vd. `docker compose -p prod ps` qua sudoers giới hạn) sẽ chốt khi làm §7 | SSH prod, đọc `.env`, sửa container prod, xem log prod chứa PII nếu bị ẩn |

Cơ chế:
- **Linux**: user `deploy`, `dev`, không thuộc nhóm `docker` (nhóm docker = root); quyền cụ thể qua `/etc/sudoers.d/`.
- **Tailscale ACL** (grants): `autogroup:member` → `*` và `tag:ci` → `100.67.135.86:22` **đã áp dụng**, kèm `tests` trong policy; `group:dev` → `unbutu:8443` (SigNoz UI) chưa làm.
- **SigNoz**: tài khoản admin (người dùng) + tài khoản `developer` role Viewer (xem log/trace/dashboard, không sửa alert/cấu hình). Kiểm chứng giới hạn role của bản Community khi dựng.
- **GitHub**: environment `production` cần duyệt + chỉ nhánh `main` **đã làm**; branch protection `main` (bắt PR + CI xanh) **chưa bật**.

---

## 8. Lộ trình senior

Mục tiêu: luyện các kỹ năng senior ngoài CI/CD + log + phân quyền + gateway. Làm theo thứ tự giai đoạn; mỗi mục xong thì **đánh dấu `[x]`, ghi ngày, và viết ADR** trong `docs/adr/NNNN-<tên>.md` (bối cảnh → quyết định → hệ quả). Bài tập sự cố ghi postmortem trong `docs/postmortems/`, runbook trong `docs/runbooks/`.

### Giai đoạn 0 – Nền (§4–§7)
- [ ] API Gateway P0 (routing, requestId, access log, JWT, timeout, CORS) – đã có: routing, requestId, timeout, access log (2026-11-16); còn JWT, CORS
- [x] 1 service mẫu end-to-end có ECS log + `trace_id`/`requestId` (2026-11-16: gateway → catalog, trace waterfall + log cùng trace_id, ADR-0006)
- [x] Observability: SigNoz (ClickHouse + OTel Collector), giữ dữ liệu 15 ngày, UI chỉ trong tailnet (2026-11-16, ADR-0006)
- [x] CI/CD: PR test → merge main → build image → prod cần duyệt, rollback theo healthcheck (2026-11-15, ADR-0003/0004/0005)
- [ ] Phân quyền: user `deploy`/`dev`, Tailscale ACL, SigNoz role, branch protection
- [x] ADR-0001: Docker Compose thay vì k3s/k8s (2026-11-15)

### Giai đoạn 1 – Ưu tiên cao
| # | Kỹ năng | Bài tập / tiêu chí hoàn thành | RAM thêm |
|---|---|---|---|
| S1 | **Metrics + Alerting** (OTel metrics → SigNoz dashboard + alert) | Dashboard RED (rate, errors, duration) mỗi service + JVM; SLO vd. 99% request < 300 ms; alert error rate > 5% hoặc service down → Telegram (webhook) | 0 (dùng SigNoz) |
| S2 | **Incident & Postmortem** | Cố ý gây ≥3 sự cố (DB chậm, memory leak, timeout dây chuyền); chỉ dùng SigNoz (log, trace, metrics) để tìm nguyên nhân; viết postmortem blameless + runbook cho từng loại | 0 |
| S3 | **DB migration zero-downtime** (Flyway, expand/contract) | Migration chạy trong pipeline; đổi tên 1 cột qua 3 lần deploy, không request nào lỗi trong lúc deploy | 0 |
| S4 | **Giao dịch phân tán** (RabbitMQ, Saga, Outbox) | Luồng đặt vé §4.2.1 (`booking → inventory → payment`) có compensation khi lỗi/hết hạn hold; Outbox không mất event; consumer idempotent; `traceId` đi qua message | ~150 MB |
| S5 | **Backup & restore drill** | `pg_dump` tự động hằng ngày ra ngoài máy; restore thử hằng tháng; ghi RPO/RTO đo được | 0 |

- [ ] S1 Metrics + Alerting
- [ ] S2 Incident drill #1 + postmortem + runbook
- [ ] S3 Flyway + expand/contract
- [ ] S4 RabbitMQ + Saga + Outbox
- [ ] S5 Backup + restore drill

### Giai đoạn 2 – Ưu tiên trung bình
| # | Kỹ năng | Bài tập / tiêu chí hoàn thành |
|---|---|---|
| S6 | **Load test & tìm bottleneck** | k6 bắn tải vào gateway tới điểm gãy; xác định nguyên nhân (connection pool, GC, CPU) bằng JFR/async-profiler, thread dump, heap dump; so sánh virtual threads (Java 21) với thread pool |
| S7 | **Chaos engineering** | Toxiproxy / `docker kill` / `tc netem` gây latency, chết service; chứng minh circuit breaker, timeout, retry hoạt động đúng thiết kế |
| S8 | **Security pipeline** | Trivy scan image + Dependabot trong CI, SBOM; secret đã chuyển sang AWS (ADR-0004) → còn: IAM Roles Anywhere thay access key dài hạn; xoay vòng key JWT; audit log cho thao tác nhạy cảm |
| S9 | **Testing chuyên nghiệp** | Testcontainers (Postgres thật trong CI); contract test giữa service (Spring Cloud Contract hoặc Pact) chặn thay đổi API phá vỡ |
| S10 | **Chiến lược deploy** | Blue-green hoặc canary qua `Weight` route ở gateway; feature flag; rollback 1 lệnh |

- [ ] S6 Load test + profiling
- [ ] S7 Chaos
- [ ] S8 Security pipeline
- [ ] S9 Testcontainers + contract test
- [ ] S10 Canary / blue-green + feature flag

### Nền tảng xuyên suốt (áp dụng từ đầu)
- [ ] **API contract**: OpenAPI (springdoc), lỗi theo **RFC 9457 Problem Details** (kèm `requestId`), phân trang, tương thích ngược.
- [ ] **Tài liệu kiến trúc**: ADR cho mọi quyết định; sơ đồ C4 (context, container) trong `docs/`.
- [ ] **Quy trình**: trunk-based, Conventional Commits, PR template + checklist self-review.

### Ràng buộc tài nguyên
- Thêm S1 + S4 → tổng ~8.5–9 / 11 GiB (§4.6). Không có staging trên server (ADR-0003).
- **Không dùng Kafka** (~1 GB+); dùng RabbitMQ (hoặc Redpanda nếu muốn API Kafka).
- Tracing waterfall đã có trong SigNoz, không cần Tempo/Jaeger riêng (ADR-0006).

---

## 9. Vận hành nhanh
```bash
ssh unbutu                                   # vào server từ mọi mạng (admin)
sudo tailscale funnel status                 # xem Funnel
sudo tailscale funnel --bg 8080              # bật lại Funnel → 127.0.0.1:8080
sudo tailscale funnel --https=443 off        # tắt public
docker compose -p prod ps | logs -f <svc>    # prod
sudo /opt/unbutu-platform/bin/deploy.sh <images.env>   # deploy tay (bình thường CI làm qua `deploy.sh -`)
# rollback: GitHub → Actions → Deploy → Run workflow (sha cũ) → Approve
sudo tail /opt/unbutu-platform/prod/deploy-history.log
sudo ufw status verbose
```
Kiểm tra từ Internet công khai (bỏ qua MagicDNS trên máy có Tailscale):
```bash
IP=$(dig +short @1.1.1.1 unbutu.tail3c11f4.ts.net A | tail -1)
curl --resolve unbutu.tail3c11f4.ts.net:443:$IP https://unbutu.tail3c11f4.ts.net/
```

---

## 10. Giới hạn & rủi ro đã biết
- Pin hỏng + chỉ 1 máy → không HA; cúp điện/mất wifi = downtime. Nên mua UPS hoặc thay pin.
- Băng thông phụ thuộc upload mạng nhà + giới hạn của Funnel; thêm 1 hop relay → độ trễ cao hơn VPS.
- Funnel chỉ hỗ trợ port công khai 443/8443/10000; domain cố định `*.ts.net`, phụ thuộc Tailscale.
- RAM: prod + SigNoz ~7 GB / 11 GiB (§4.6), ClickHouse phải giới hạn bộ nhớ; không có staging nên thay đổi đi thẳng từ local lên prod – phụ thuộc vào test (Testcontainers) + duyệt deploy + rollback.
- Server dùng **access key dài hạn** (IAM user `unbutu-server`, chỉ đọc) ở `/root/.aws` – lộ root = lộ cấu hình prod. Nợ kỹ thuật: IAM Roles Anywhere (ADR-0004).
- Laptop 2015, 1 ổ đĩa, không RAID → cần **backup Postgres** định kỳ (pg_dump ra ngoài máy) – chưa làm.

## 11. Lộ trình nâng cấp hạ tầng
- **Có tên miền** → Cloudflare Tunnel (`cloudflared` → `127.0.0.1:8080`), chỉ đổi URL trong `vercel.json`.
- **Cần ổn định** → chuyển `deploy/` sang VPS, đổi URL trong `vercel.json`. Không đổi code.
- **Đổi/thêm backend observability** (Grafana LGTM, Datadog…) → chỉ đổi exporter ở OTel Collector, không sửa service (ADR-0006).
- **Học K8s** → chuyển compose sang manifest k3s, giữ nguyên image.

## 12. Quyết định
Đã chốt:
- [x] **Vé QR trong notification-service** (2026-11-21, ADR-0009): nghe `booking.confirmed` → vé + QR JWS ES256 (verify offline qua JWKS); UI hiện QR ngay sau thanh toán + lịch sử `/orders`. Email là bước sau. Package `ticket` tách được thành ticketing-service.
- [x] **kafka-ui chỉ đọc** (kafbat, project `prod`) xem Redpanda để trace bug – `127.0.0.1:8091` → `tailscale serve :9443`, không Funnel; sửa dữ liệu Kafka chỉ bằng `rpk` qua SSH (ADR-0008, 2026-11-21).
- [x] Docker Compose (không k3s/k8s).
- [x] Đo đạc service: **OpenTelemetry Java agent** (chỉ prod), log JSON ECS stdout có `trace_id`/`span_id`/`requestId` (ADR-0006, 2026-11-16).
- [x] Observability: **SigNoz tự host** (log + metrics + trace + alert), đo đạc theo OpenTelemetry; trace qua `traceId` + `X-Request-Id` (ADR-0006, thay ELK + Prometheus/Grafana).
- [x] 2 môi trường: **local** (MacBook) + **prod** (`unbutu`, qua CI/CD); không staging vì thiếu RAM (ADR-0003). Merge `main` → build image một lần → deploy prod cần duyệt (GitHub Environment `production`).
- [x] Bài toán: đặt vé sự kiện/phim, 7 service (§4.2) + 2 thư viện dùng chung.
- [x] Gradle Kotlin DSL, Java 21 (ADR-0002). Package gốc `com.unbutu.ticket`.
- [x] Message broker: ~~RabbitMQ~~ → **Redpanda (Kafka API) + Transactional Outbox** (ADR-0007, 2026-11-21; khớp đích Kafka/MSK). 1 node, prod chế độ production (`--memory=1G`, có fsync), local developer mode 512 MB. Lý do đổi: event log replay được, consumer group scale ngang, cùng API với đích AWS.
- [x] **Tồn kho** (2026-11-21): Redis + Lua giữ bộ đếm + hold có TTL (chống oversell atomic); **Postgres `inventory_db` là sổ cái** (đã bán) – Redis mất dữ liệu thì dựng lại từ Postgres. Giới hạn **4 vé / tài khoản / sự kiện** (cộng đơn đang giữ + đã thanh toán).
- [x] **Mỗi service = 1 module = 1 image = 1 container**, cả trên server lẫn local.
- [x] **1 container Postgres dùng chung, mỗi service 1 database + 1 user** chỉ có quyền trên database của mình (`identity_db`, `catalog_db`, ...). Service chỉ tạo/sửa bảng của mình qua Flyway riêng; không đọc bảng service khác (gọi API/nghe event).
- [x] **Chạy local kiểu hybrid**: mặc định mọi service trong container (`make up`); service đang code chạy trong IntelliJ với profile `local` (`make ide s=<service>`), gateway trong container gọi nó qua `host.docker.internal`. Mỗi service 1 port cố định trên máy (8080 gateway, 8081 identity, 8082 catalog, 8083 inventory, 8084 booking, 8085 payment, 8086 notification). Trên server service vẫn không mở port ra ngoài.
- [x] Cấu hình + secret prod: repo private `pro-ticket-workload` (Terraform/GitOps, OIDC) giữ đường dẫn, value chỉ trong AWS Secrets Manager; `deploy.sh` lấy lúc deploy (ADR-0004).
- [x] Spring Boot 4.1.2, Spring Cloud 2025.1.4 (Gateway 5.0.4), PostgreSQL 17.
- [x] CD: deploy prod sau khi duyệt, qua Tailscale OIDC (`tag:ci`) + SSH forced command (ADR-0005).
- [x] **Phân quyền API ghi tại service** (2026-11-21): catalog tự verify access token (RS256, JWKS nội bộ `http://identity-service:8080/.well-known/jwks.json`, `iss`, `token_use=access`), role lấy từ claim `roles`. Đường dẫn: đọc công khai `/api/catalog/**` (GET), ghi chỉ `/api/catalog/admin/**` (ADMIN). Gateway verify JWT (§4.4 P0) làm sau – service vẫn giữ kiểm tra (defense in depth).
- [x] **Đường đọc của khách thiết kế cho 50k rps** (2026-11-21): API công khai catalog trả `Cache-Control: public, max-age=30, stale-while-revalidate` (CDN/edge gánh tải), URL không chứa thời gian/user (server tự lấy "now" làm tròn phút), phân trang Slice (không `COUNT`), 1 query có index, không N+1; API công khai không đọc token. Bước sau khi cần: cache trong app + read replica. Tồn kho realtime (A8b) tách sang inventory + Redis.
- [x] **Thanh toán PAY-1** (2026-11-21): cổng giả lập nói đúng giao thức VNPay 2.1.0 (đổi sang sandbox = đổi cấu hình). IPN là nguồn sự thật; payment → `payment.result.v1` qua Outbox. Booking quyết toán trong consumer: gọi **đồng bộ** inventory `commit` / payment `refund` (cả hai idempotent, consumer retry vô hạn có backoff) – lệch có chủ đích so với thiết kế đích (`inventory.command.v1` qua Kafka) cho gọn; đổi sang command topic khi cần tách tải. Trả muộn: còn vé → `CONFIRMED`, hết vé → hoàn tiền; sổ cái inventory (`sold + n ≤ total`) là chốt chặn cuối chống oversell.
- [x] **Cổng thanh toán = giả lập để luyện lỗi** (2026-11-21, người dùng chọn; không tích hợp VNPay sandbox): ngân hàng giả lập có sổ sách + hàng đợi IPN bền + querydr; lỗi thường gặp bật bằng env (`IpnFault` cắm thêm, mặc định happy case). Job đối soát (1′, treo > 2′) dùng querydr như với VNPay thật. Runbook `docs/runbooks/payment-drills.md`.
- [x] GitHub repo **public**: https://github.com/MarkusKeithThomas/unbutu-platform (commit author `chthuong` + email noreply GitHub).

Còn mở:
- [ ] Frontend: React + Vite (đề xuất)?
- [ ] Bật BIOS "Power On When AC Detected" – chờ người dùng xác nhận.
- [ ] Chiến lược backup DB.
- [ ] Có tắt SSH bằng mật khẩu (chỉ dùng key) không?
