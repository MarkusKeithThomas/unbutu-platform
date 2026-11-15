# Nhật ký các bước setup (2026-11-15)

Ghi lại **thứ tự đã làm thật**: mỗi bước làm gì, ở đâu, kiểm chứng ra sao. Thiết kế tổng thể: [ARCHITECTURE.md](../ARCHITECTURE.md);
lý do của các quyết định: [docs/adr/](adr/). Khi lặp lại setup (máy mới, VPS), đi theo đúng thứ tự này.

## Giai đoạn A – Server & truy cập

| # | Làm gì | Ở đâu | Kiểm chứng |
|---|---|---|---|
| A1 | SSH key riêng `~/.ssh/id_ed25519_unbutu` (key cũ có passphrase không nhớ); `~/.ssh/config`: `unbutu` (Tailscale), `unbutu-lan` (mDNS) | Mac | `ssh unbutu` không hỏi mật khẩu |
| A2 | Tailscale trên Mac + server; tắt key expiry của node | Mac, server, admin Tailscale | `ssh unbutu` từ mạng khác |
| A3 | Funnel `443 → 127.0.0.1:8080` (`tailscale funnel --bg 8080`) | server | curl link `ts.net` qua DNS public |
| A4 | ufw, không sleep, tự boot sau mất điện (GRUB), unattended-upgrades, Docker log rotation, sshd hardening, smartmontools | server | reboot thử: mọi thứ tự lên lại |

## Giai đoạn B – Repo & code (`unbutu-platform`)

| # | Làm gì | Ở đâu | Kiểm chứng |
|---|---|---|---|
| B1 | Khung monorepo Gradle 9.9.1 Kotlin DSL, version catalog, convention plugin (`buildSrc`), Java 21, Spring Boot 4.1.2, Spring Cloud 2025.1.4 (ADR-0002) | `settings.gradle.kts`, `gradle/libs.versions.toml`, `buildSrc/` | `./gradlew build` |
| B2 | Lombok có kiểm soát (`lombok.config` cấm `@Data`/`@Value`/`@EqualsAndHashCode`) | `lombok.config` | class dùng `@Data` → lỗi biên dịch |
| B3 | `catalog-service`: Postgres + Flyway (`ddl-auto: validate`), API `/api/catalog/events`, Testcontainers | `services/catalog-service` | integration test với Postgres thật |
| B4 | `api-gateway`: route `/api/catalog/**`, `RequestIdWebFilter` (giữ/sinh `X-Request-Id`), timeout | `services/api-gateway` | test filter + route |
| B5 | Dockerfile dùng chung: layered jar, JRE 21, user không phải root | `deploy/docker/service.Dockerfile` | `docker build` |
| B6 | Local: 1 container Postgres, mỗi service 1 DB + user (REVOKE PUBLIC); chế độ hybrid IntelliJ (`make ide s=…`) | `deploy/compose.local.yml`, `deploy/postgres/init/`, `Makefile` | user lạ → `permission denied`; gateway → IntelliJ |
| B7 | Dữ liệu mẫu chỉ ở local/test: `db/seed-local/R__seed_local_events.sql` (profile `local`, compose local, integration test) | `catalog-service` | prod: `GET /api/catalog/events` → `[]` |

## Giai đoạn C – CI và build image (PR #1, #2)

| # | Làm gì | Ở đâu | Kiểm chứng |
|---|---|---|---|
| C1 | `ci.yml`: mỗi PR và push `main` → build + test + build thử image; `cancel-in-progress` chỉ cho PR | `.github/workflows/ci.yml` | PR #1 xanh |
| C2 | `release.yml`: CI xanh trên `main` → build image **1 lần** → push `ghcr.io/markuskeiththomas/<svc>:<sha>` → artifact `images-<sha>` (`images.env`: image **theo digest** + `APP_VERSION` + `GIT_SHA`, giữ 90 ngày) | `.github/workflows/release.yml` | server `docker pull` theo digest |
| C3 | Chỉ 2 môi trường: local + prod, bỏ staging (thiếu RAM) – ADR-0003 | `docs/adr/0003-…` | – |

## Giai đoạn D – Cấu hình & secret prod (repo private `pro-ticket-workload`, ADR-0004)

| # | Làm gì | Ở đâu | Kiểm chứng |
|---|---|---|---|
| D1 | Terraform 1.17.2 (brew `hashicorp/tap`, cần cập nhật Command Line Tools); `aws configure` bằng IAM user admin `ticket-user` (không dùng root) | Mac | `aws sts get-caller-identity` |
| D2 | `bootstrap/` (admin apply **từ Mac 1 lần**): bucket state S3 `pro-ticket-tfstate-038774852192` (versioning, mã hoá, chỉ TLS), GitHub OIDC provider, role `gha-pro-ticket-workload-plan` (PR, ReadOnly) + `-apply` (chỉ `main`, chỉ `unbutu/*`), permissions boundary; state chuyển vào S3 | `pro-ticket-workload/bootstrap` | plan lại → `No changes` |
| D3 | Trust OIDC theo **immutable subject** (`repo:<owner>@<id>/<repo>@<id>:…`) – dạng tên repo cũ bị AWS từ chối | `bootstrap/versions.tf` | CI plan xanh |
| D4 | `terraform.yml`: PR → plan + comment; merge → apply | `pro-ticket-workload/.github/workflows` | comment plan trên PR |
| D5 | Mỗi service 1 folder `prod/services/<svc>/env.yaml`: `env` (giá trị thường) + `secrets` (**chỉ đường dẫn** `<secret>#<key>`) → SSM `/unbutu/prod/<svc>/env-map`; Terraform chỉ kiểm tra secret tồn tại, **không đọc value** | `pro-ticket-workload/prod` | plan lỗi khi trỏ sai/secret không có |
| D6 | Value nhập trên AWS Console (Secrets Manager, 1 secret/service, nhiều key, tag `ManagedBy=console`) | AWS | – |
| D7 | Thu hẹp quyền CI: không role nào có `GetSecretValue` | `bootstrap` (pha 2) | `simulate-principal-policy` → `implicitDeny` |
| D8 | IAM user `unbutu/unbutu-server` (boundary, chỉ đọc env-map + secret được trỏ tới); access key **tạo tay**, đi thẳng qua SSH vào `/root/.aws` (600); AWS CLI v2 trên server (kiểm chữ ký GPG) | server | đọc được; ghi / S3 / IAM → `AccessDenied` |

## Giai đoạn E – Deploy prod (PR #3 → #6)

| # | Làm gì | Ở đâu | Kiểm chứng |
|---|---|---|---|
| E1 | `/opt/unbutu-platform/{bin,prod}` (root); user `deploy` (không nhóm docker, không shell sudo) | server | – |
| E2 | `deploy.sh`: kiểm `images.env` → sinh `env/<svc>.env` (root 600) từ AWS → tải `compose.app.yml` + `postgres/init` đúng `GIT_SHA` → pull (thử 3 lần) → `up --wait` → smoke test → lỗi thì **rollback** | `deploy/scripts/deploy.sh` → server `/opt/unbutu-platform/bin/` (**admin cài tay**) | deploy tay `c24ab06`: OK |
| E3 | `compose.app.yml`: `env_file` (format raw), không còn `.env`; chỉ gateway bind `127.0.0.1:8080` | `deploy/compose.app.yml` | – |
| E4 | GitHub Environment `production`: người duyệt bắt buộc, chỉ nhánh `main`; secret `DEPLOY_SSH_KEY`; biến `DEPLOY_KNOWN_HOSTS`, `TS_OIDC_CLIENT_ID`, `TS_OIDC_AUDIENCE` | GitHub Settings | – |
| E5 | Server: `authorized_keys` của `deploy` = `restrict,from="100.64.0.0/10",command="sudo -n …/deploy.sh -"`; sudoers `/etc/sudoers.d/unbutu-deploy` chỉ cho đúng lệnh đó | server | `deploy` chạy lệnh khác → `a password is required` |
| E6 | Tailscale: `tagOwners` `tag:ci`; grants: `autogroup:member` → `*`, `tag:ci` → `100.67.135.86:22`; `tests` trong policy; trust credential OIDC (subject `…:environment:production`, Auth Keys write, `tag:ci`) | admin Tailscale | lưu policy được (tests đạt) |
| E7 | `deploy.yml`: chờ Approve → artifact `images-<sha>` → Tailscale OIDC → `ssh deploy@unbutu` → kiểm tra **qua DNS public** (MagicDNS trong tailnet trả IP 100.x và ACL chặn :443) | `.github/workflows/deploy.yml` | deploy `b47b24b`, `4551749` qua CI |

## Sự cố gặp trong lúc setup

| Khi | Hiện tượng | Nguyên nhân | Xử lý |
|---|---|---|---|
| D3 | CI không assume được role AWS | Repo mới dùng immutable OIDC subject | Trust theo `repo:<owner>@<id>/<repo>@<id>` |
| D5 | Job Apply lỗi `ssm:PutParameter` AccessDenied | Merge PR trước khi admin apply bootstrap | Apply bootstrap rồi re-run job – **luôn apply bootstrap trước khi merge PR phụ thuộc** |
| E7 | Bước kiểm tra cuối timeout dù deploy OK | Runner trong tailnet → MagicDNS → 443 bị ACL chặn (đúng thiết kế) | Phân giải qua `1.1.1.1` + `curl --resolve` |
| E7 | Deploy `4551749` lỗi → tự rollback về `b47b24b` | Mạng nhà → GHCR `connection reset by peer` khi pull | Re-run OK; `deploy.sh` thử pull 3 lần |
