# Kiến trúc hệ thống – unbutu platform

> Tài liệu nguồn sự thật (source of truth) về hạ tầng và kiến trúc. Cập nhật file này mỗi khi hạ tầng hoặc quyết định kiến trúc thay đổi.
> Trạng thái: **Hạ tầng đã sẵn sàng · Kiến trúc đã chốt Compose + ELK · Ứng dụng chưa build** (cập nhật 2026-11-15)

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
┌──────────────── Docker network nội bộ "backend" ────────────────┐
│ api-gateway (Spring Cloud Gateway) :8080  ← DUY NHẤT publish port │
│   /api/users/**    → http://user-service:8080                     │
│   /api/products/** → http://product-service:8080                  │
│   /api/orders/**   → http://order-service:8080                    │
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
- Container thử nghiệm `hello` (`nginxdemos/hello:plain-text`) đang chiếm `127.0.0.1:8080` → **xóa khi deploy gateway thật**: `docker rm -f hello`.

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
| G2 | Đóng vai người trace bug: tìm lỗi qua log tập trung, không SSH | Elasticsearch + Kibana + Filebeat | §5 |
| G3 | Làm việc với hệ thống **theo phân quyền** (dev không có quyền vào prod) | Linux user, Tailscale ACL, Kibana RBAC, GitHub Environments | §7 |
| G4 | Nghiên cứu & cài các system design bắt buộc ở **API Gateway** | Spring Cloud Gateway + Redis | §4 |
| G5 | **requestId/traceId xuyên suốt mọi service**, tra trên Elasticsearch | Micrometer Tracing (W3C `traceparent`) + `X-Request-Id` + ECS JSON log | §5 |
| G6 | Lộ trình kỹ năng senior: metrics, incident, migration, saga, backup, load test, chaos, security | Xem checklist | §8 |

---

## 4. Kiến trúc ứng dụng

### 4.1 Sơ đồ (trên unbutu)
```
Internet ─▶ Funnel :443 ─▶ 127.0.0.1:8080 (prod gateway)
Tailnet  ─▶ unbutu:5601 (Kibana)                                   ← chỉ trong tailnet
(Không có staging trên server – môi trường thứ 2 là local trên MacBook, ADR-0003.)

┌─ project "prod" (network prod_backend) ─┐
│ api-gateway ─┬─▶ identity / catalog     │
│   + redis    ├─▶ booking ─▶ inventory   │
│              └─▶ payment                │
│ rabbitmq ─▶ notification                │
│ postgres (DB riêng/service)             │
└──────────────┬──────────────────────────┘
               │ stdout JSON log (ECS)
               ▼
        ┌─ project "observability" ───────────────────────────────┐
        │ filebeat (đọc log Docker) ─▶ elasticsearch ◀─ kibana     │
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
| `notification-service` | Email/Telegram xác nhận vé; chỉ tiêu thụ event | `notification_db` | consumer idempotent |
| `postgres` | PostgreSQL 17, 1 database + user riêng/service | | nội bộ |
| `redis` | Rate limit (gateway), seat hold TTL (inventory) | | nội bộ |
| `rabbitmq` | Event bất đồng bộ giữa service | | nội bộ |
| `elasticsearch` / `kibana` / `filebeat` | Log tập trung | | project `observability` |
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
| RBAC | Linux user + Tailscale ACL + Kibana role (§7) |

### 4.4 API Gateway – các hạng mục system design (nghiên cứu & cài dần)
| Ưu tiên | Hạng mục | Cách làm (Spring Cloud Gateway) |
|---|---|---|
| P0 | Routing theo path `/api/<domain>/**` | `RouteLocator` / `spring.cloud.gateway.routes` |
| P0 | **Request ID / correlation** | Global filter: nhận hoặc sinh `X-Request-Id`, đẩy xuống downstream, trả về response header; tracing tự sinh `traceparent` |
| P0 | Access log có cấu trúc | Global filter log: method, path, status, latency, client IP, userId, traceId |
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
| P2 | Metrics | Actuator + Micrometer (`/actuator/prometheus`) – Prometheus/Grafana để sau |
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
| prod: 7 service Spring Boot (~350 MB) + postgres + redis + rabbitmq | ~3 GB |
| observability: ES (heap 1g, limit 2g) + Kibana + Filebeat | ~3 GB |
| OS + Docker + Tailscale | ~1 GB |
| **Tổng** | **~7 GB** – còn ~4 GB dư cho S1/S4 (§8) và đỉnh tải; vẫn cân nhắc `-XX:+UseSerialGC`, giới hạn thread pool |

---

## 5. Logging & tracing (G2, G5)

### 5.1 Luồng log
```
service (Logback, ECS JSON → stdout) → Docker json-file → filebeat (autodiscover, add_docker_metadata)
  → elasticsearch: data stream logs-<env>-*   (ILM: xoá sau 7 ngày)
  → kibana: Discover / dashboard
```

### 5.2 requestId xuyên service
1. Request vào gateway: nếu có `X-Request-Id` thì dùng, không thì sinh UUID. Micrometer Tracing đồng thời tạo `traceId` và header W3C `traceparent`.
2. Gateway forward kèm `X-Request-Id` + `traceparent` → service A.
3. Service A: tracing đọc `traceparent` → cùng `traceId`; filter đọc `X-Request-Id` vào **MDC** + baggage để tự đi tiếp.
4. A gọi B bằng `RestClient` (inject) → header được propagate tự động.
5. Mọi dòng log có `trace.id` + `requestId`. Response trả `X-Request-Id` cho client; lỗi JSON cũng chứa `requestId`.
6. (Sau này có RabbitMQ/Kafka) propagate header vào message.

### 5.3 Quy trình trace bug (vai trò dev)
User báo lỗi + `requestId` (hiện trên màn hình lỗi FE) → Kibana Discover → query `requestId : "<id>"` (hoặc `trace.id`) → thấy log theo thứ tự thời gian ở gateway → order-service → user-service → tìm `log.level : ERROR` + stacktrace → xác định service/commit (`service.version` = git SHA của image).

---

## 6. CI/CD (G1)

### 6.1 Repo (monorepo, Gradle Kotlin DSL – đã tạo khung)
```
unbutu-platform/
├── CLAUDE.md · ARCHITECTURE.md · README.md
├── settings.gradle.kts · gradlew · gradle/wrapper/     # Gradle 9.9.1, Java 21
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
│   ├── postgres/init/          # tạo database cho từng service
│   ├── filebeat/
│   ├── scripts/deploy.sh       # deploy prod trên server: env từ AWS + compose theo digest + rollback
│   ├── compose.app.yml         # prod trên server (image theo digest, env_file từ deploy.sh)
│   └── compose.local.yml
├── docs/{adr,postmortems,runbooks,diagrams}/
├── tools/{k6,chaos}/
└── .github/{workflows/,pull_request_template.md}
```
Sẽ thêm khi làm CI/CD: `gradle/libs.versions.toml`, `build.gradle.kts` từng module, `Dockerfile` từng service, `deploy/compose.app.yml`, `deploy/compose.observability.yml`, workflow `ci.yml`/`release.yml`.

### 6.2 Pipeline
| Sự kiện | Việc làm | Môi trường |
|---|---|---|
| Mở/cập nhật PR | build, unit + integration test, build thử image (`ci.yml`, image bỏ đi) | – |
| Merge `main` | `ci.yml` chạy lại trên main; CI xanh → `release.yml`: build image **một lần** → push `ghcr.io/<user>/<svc>:<sha>` → job deploy (environment `production`) **chờ duyệt** → deploy theo digest | **prod** |
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
3. `docker compose -p prod pull && up -d --wait` + smoke test `GET /api/catalog/ping` qua gateway;
4. lỗi → rollback về `images.current.env` (lần thành công trước). Lịch sử: `/opt/unbutu-platform/prod/deploy-history.log`.

Cấu hình/secret (ADR-0004): repo private `pro-ticket-workload` (Terraform, PR → plan, merge → apply qua GitHub OIDC) giữ bản đồ biến từng service; value nhập trên AWS Console; server đọc bằng IAM user chỉ đọc `unbutu-server`.

Lưu ý: GitHub Environments có **required reviewers** cho repo **public** miễn phí; repo private cần gói trả phí → nếu private, thay bằng `workflow_dispatch` thủ công.

---

## 7. Phân quyền (G3) – mô phỏng "dev không vào được prod"

| Vai trò | Ai | Được làm | Không được |
|---|---|---|---|
| **admin** | `chauhoaithuong` (máy MacBook) | Mọi thứ: SSH sudo, cấu hình server | – |
| **deployer** | GitHub Actions (`tag:ci`) | SSH user `deploy`, chỉ chạy `deploy.sh` (forced command trong `authorized_keys`) | shell tự do, sudo |
| **developer** | Tài khoản Tailscale thứ 2 / thiết bị thứ 2 (đóng vai nhân viên) | Kibana (role chỉ đọc `logs-*`) – trace bug qua log, không SSH. Quyền thêm (vd. `docker compose -p prod ps` qua sudoers giới hạn) sẽ chốt khi làm §7 | SSH prod, đọc `.env`, sửa container prod, xem log prod chứa PII nếu bị ẩn |

Cơ chế:
- **Linux**: user `deploy`, `dev`, không thuộc nhóm `docker` (nhóm docker = root); quyền cụ thể qua `/etc/sudoers.d/`.
- **Tailscale ACL**: `tag:ci` → `unbutu:22` (user deploy); `group:dev` → `unbutu:5601`; admin → `*`.
- **Kibana/ES security** (bản free có RBAC): user `elastic` (admin), role `developer` (read `logs-*`, Discover), có thể tách quyền xem `logs-prod-*`.
- **GitHub**: branch protection `main` (bắt PR + CI xanh), environment `production` cần duyệt.

---

## 8. Lộ trình senior

Mục tiêu: luyện các kỹ năng senior ngoài CI/CD + log + phân quyền + gateway. Làm theo thứ tự giai đoạn; mỗi mục xong thì **đánh dấu `[x]`, ghi ngày, và viết ADR** trong `docs/adr/NNNN-<tên>.md` (bối cảnh → quyết định → hệ quả). Bài tập sự cố ghi postmortem trong `docs/postmortems/`, runbook trong `docs/runbooks/`.

### Giai đoạn 0 – Nền (§4–§7)
- [ ] API Gateway P0 (routing, requestId, access log, JWT, timeout, CORS)
- [ ] 1 service mẫu end-to-end có ECS log + `traceId`/`requestId`
- [ ] Observability: Elasticsearch + Kibana + Filebeat, ILM 7 ngày
- [ ] CI/CD: PR test → merge main → build image → prod cần duyệt, rollback theo healthcheck
- [ ] Phân quyền: user `deploy`/`dev`, Tailscale ACL, Kibana role, branch protection
- [x] ADR-0001: Docker Compose thay vì k3s/k8s (2026-11-15)

### Giai đoạn 1 – Ưu tiên cao
| # | Kỹ năng | Bài tập / tiêu chí hoàn thành | RAM thêm |
|---|---|---|---|
| S1 | **Metrics + Alerting** (Prometheus, Grafana, Alertmanager) | Dashboard RED (rate, errors, duration) mỗi service + JVM; SLO vd. 99% request < 300 ms; alert error rate > 5% hoặc service down → Telegram | ~400 MB |
| S2 | **Incident & Postmortem** | Cố ý gây ≥3 sự cố (DB chậm, memory leak, timeout dây chuyền); chỉ dùng Kibana/Grafana để tìm nguyên nhân; viết postmortem blameless + runbook cho từng loại | 0 |
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
| S8 | **Security pipeline** | Trivy scan image + Dependabot trong CI, SBOM; secret mã hoá bằng sops + age thay `.env` thô; xoay vòng key JWT; audit log cho thao tác nhạy cảm |
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
- Chưa thêm Tempo/Jaeger; `traceId` trên Kibana là đủ cho giai đoạn này.

---

## 9. Vận hành nhanh
```bash
ssh unbutu                                   # vào server từ mọi mạng (admin)
sudo tailscale funnel status                 # xem Funnel
sudo tailscale funnel --bg 8080              # bật lại Funnel → 127.0.0.1:8080
sudo tailscale funnel --https=443 off        # tắt public
docker compose -p prod ps | logs -f <svc>    # prod
sudo /opt/unbutu-platform/bin/deploy.sh <images.env>   # deploy tay (CI làm việc này)
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
- RAM: prod + ELK ~7 GB / 11 GiB (§4.6); không có staging nên thay đổi đi thẳng từ local lên prod – phụ thuộc vào test (Testcontainers) + duyệt deploy + rollback.
- Server dùng **access key dài hạn** (IAM user `unbutu-server`, chỉ đọc) ở `/root/.aws` – lộ root = lộ cấu hình prod. Nợ kỹ thuật: IAM Roles Anywhere (ADR-0004).
- Laptop 2015, 1 ổ đĩa, không RAID → cần **backup Postgres** định kỳ (pg_dump ra ngoài máy) – chưa làm.

## 11. Lộ trình nâng cấp hạ tầng
- **Có tên miền** → Cloudflare Tunnel (`cloudflared` → `127.0.0.1:8080`), chỉ đổi URL trong `vercel.json`.
- **Cần ổn định** → chuyển `deploy/` sang VPS, đổi URL trong `vercel.json`. Không đổi code.
- **Distributed tracing dạng waterfall** → OpenTelemetry Collector + Jaeger/Tempo (cùng traceId đã có) – khi có thêm RAM.
- **Học K8s** → chuyển compose sang manifest k3s, giữ nguyên image.

## 12. Quyết định
Đã chốt:
- [x] Docker Compose (không k3s/k8s).
- [x] Log tập trung: Elasticsearch + Kibana + Filebeat; trace qua `traceId` + `X-Request-Id`.
- [x] 2 môi trường: **local** (MacBook) + **prod** (`unbutu`, qua CI/CD); không staging vì thiếu RAM (ADR-0003). Merge `main` → build image một lần → deploy prod cần duyệt (GitHub Environment `production`).
- [x] Bài toán: đặt vé sự kiện/phim, 7 service (§4.2) + 2 thư viện dùng chung.
- [x] Gradle Kotlin DSL, Java 21 (ADR-0002). Package gốc `com.unbutu.ticket`.
- [x] Message broker: RabbitMQ.
- [x] **Mỗi service = 1 module = 1 image = 1 container**, cả trên server lẫn local.
- [x] **1 container Postgres dùng chung, mỗi service 1 database + 1 user** chỉ có quyền trên database của mình (`identity_db`, `catalog_db`, ...). Service chỉ tạo/sửa bảng của mình qua Flyway riêng; không đọc bảng service khác (gọi API/nghe event).
- [x] **Chạy local kiểu hybrid**: mặc định mọi service trong container (`make up`); service đang code chạy trong IntelliJ với profile `local` (`make ide s=<service>`), gateway trong container gọi nó qua `host.docker.internal`. Mỗi service 1 port cố định trên máy (8080 gateway, 8081 identity, 8082 catalog, 8083 inventory, 8084 booking, 8085 payment, 8086 notification). Trên server service vẫn không mở port ra ngoài.
- [x] Cấu hình + secret prod: repo private `pro-ticket-workload` (Terraform/GitOps, OIDC) giữ đường dẫn, value chỉ trong AWS Secrets Manager; `deploy.sh` lấy lúc deploy (ADR-0004).
- [x] GitHub repo **public**: https://github.com/MarkusKeithThomas/unbutu-platform (commit author `chthuong` + email noreply GitHub).

Còn mở:
- [ ] Frontend: React + Vite (đề xuất)?
- [ ] DB: PostgreSQL (đề xuất)?
- [ ] Phiên bản Spring Boot / Spring Cloud.
- [ ] Bật BIOS "Power On When AC Detected" – chờ người dùng xác nhận.
- [ ] Chiến lược backup DB.
- [ ] Có tắt SSH bằng mật khẩu (chỉ dùng key) không?
