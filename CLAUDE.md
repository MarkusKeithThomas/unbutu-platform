# CLAUDE.md

Đọc **Handoff** (cuối file) trước. Chỉ mở **đúng mục cần** của `ARCHITECTURE.md` (~400 dòng) / `docs/setup-steps.md` / ADR khi việc đang làm đụng tới – không đọc hết để tiết kiệm token.

## Tóm tắt nhanh
- Microservice Java Spring Boot, mỗi container = 1 service, Docker Compose trên 1 máy (`unbutu`, Ubuntu 24.04).
- Public: Vercel (UI) → `https://unbutu.tail3c11f4.ts.net` (Tailscale Funnel) → `127.0.0.1:8080` (api-gateway).
- Truy cập server: `ssh unbutu` (qua Tailscale, có passwordless sudo). Dùng `ssh -o BatchMode=yes unbutu '...'` cho lệnh không tương tác.

## Quy tắc khi làm việc
- **Bắt đầu mỗi việc mới**: `git checkout main && git pull --ff-only`, rồi tạo nhánh mới từ `main` (`feat/…`, `fix/…`). Không làm tiếp trên nhánh của PR cũ (PR có thể đã merge/squash).
- **Thay đổi không phải code** (`*.md`, ADR, runbook, `docs/`): commit vào nhánh dài hạn **`docs/wip`**, không mở PR riêng. Mỗi lần thêm: `git fetch && git rebase origin/main`. Lý do: mỗi lần merge vào `main` đều chạy CI → Release → Deploy (tốn build). Khi có PR code: `git rebase docs/wip` hoặc cherry-pick các commit docs vào nhánh code, để **1 lần merge = 1 lần build**. Sau khi merge xong, reset `docs/wip` về `main`.
- `private-docs/`: ghi chép cá nhân, bị loại trong `.git/info/exclude` – **không commit/push**.
- Trên server: chỉ `api-gateway` được publish port, và chỉ `127.0.0.1:8080` (ngoại lệ duy nhất: SigNoz UI `127.0.0.1:3301` → `tailscale serve :8443`, chỉ tailnet – ADR-0006). Docker bỏ qua ufw – không bao giờ publish ra `0.0.0.0`. (Local: mỗi service mở port cố định trên `127.0.0.1` để debug hybrid.)
- API công khai dưới `/api/<domain>/**`; service gọi nhau bằng `http://<service-name>:8080`, URL từ biến môi trường.
- Log ra stdout dạng JSON ECS, luôn có `trace.id` + `requestId`; gọi service khác chỉ qua `RestClient.Builder`/`WebClient.Builder` được inject (để propagate trace header).
- 2 môi trường: local (MacBook) và prod (server, qua CI/CD) – không có staging. 2 compose project trên server: `prod` (Funnel → 127.0.0.1:8080), `observability` (SigNoz: ClickHouse + OTel Collector – `deploy/compose.observability.yml`, admin chạy tay theo `docs/runbooks/signoz.md`, ADR-0006).
- Cấu hình + secret prod (ADR-0004): biến của service khai báo trong repo private `~/pro-ticket-workload` (`prod/services/<svc>/env.yaml`, PR → plan, merge → apply); value chỉ ở AWS Secrets Manager. Không có `.env` gõ tay, không đưa value vào git/Terraform/log. Deploy prod chỉ qua `deploy.sh` (`deploy/scripts/`).
- `pro-ticket-workload/bootstrap/` chỉ admin apply từ Mac – Claude chuẩn bị `plan -out`, người dùng tự chạy `apply`.
- Thêm service mới: thêm folder `prod/services/<service>/env.yaml` ở `pro-ticket-workload` + service vào `deploy/compose.app.yml` (`env_file: env/<service>.env`, format raw); thêm vào `deploy/compose.local.yml` (port cố định theo bảng trong README, `depends_on` chỉ hạ tầng), tạo `application-local.yml`, thêm database vào `SERVICE_DATABASES`, thêm `PORT_<service>` trong Makefile nếu chưa có.
- Làm việc với người dùng: từng bước nhỏ, giải thích mỗi bước, hỏi trước khi cài công cụ hoặc chọn hướng thiết kế.
- Không bật Tailscale SSH, không đổi ufw/Funnel/sleep config khi chưa hỏi người dùng.
- Khi hạ tầng hoặc quyết định thay đổi, cập nhật `ARCHITECTURE.md` (gồm mục "Quyết định").
- Xong một mục trong "Lộ trình senior" (§8): đánh dấu `[x]` + ngày, viết ADR `docs/adr/NNNN-<tên>.md`; sự cố/bài tập incident → `docs/postmortems/`, runbook → `docs/runbooks/`.

## Handoff (cập nhật cuối mỗi phiên – lần cuối: 2026-11-16)

**Trạng thái**
- Prod chạy trên `unbutu`: api-gateway + catalog-service + postgres. Public: `https://unbutu.tail3c11f4.ts.net` (`/api/catalog/ping`, `/api/catalog/events` = `[]`, demo `/api/catalog-service/bich-phuong` – có từ bản `aed92d4`, cần người dùng Approve deploy).
- CI/CD xong (ADR-0003/4/5): merge `main` → `ci.yml` → `release.yml` (image 1 lần, `images.env` theo digest) → `deploy.yml` **chờ người dùng Approve** → Tailscale OIDC (`tag:ci`) → `ssh deploy@100.67.135.86` (forced command) → `deploy.sh -`. Rollback: Actions → Deploy → Run workflow (SHA cũ).
- Observability **đã chốt SigNoz tự host + OpenTelemetry** (ADR-0006) – **đang chạy** từ 2026-11-16 (bản `999200b`, `/opt/unbutu-platform/observability`), UI `https://unbutu.tail3c11f4.ts.net:8443` (tailscale serve, chỉ tailnet), admin đã đăng ký. Đã thu log Docker của project `prod` (text + JSON); chưa có trace/metrics. Còn mở: Micrometer + OTLP (đề xuất) hay OTel Java agent.
- Cấu hình/secret prod: repo private `~/pro-ticket-workload` (Terraform, AWS `038774852192`, `ap-southeast-1`). Biến service: `prod/services/<svc>/env.yaml` (`env` + `secrets: <secret>#<key>`). Value chỉ trên AWS Console.

**Việc tiếp theo (gợi ý thứ tự, hỏi người dùng trước khi bắt đầu)**
1. Branch protection `main` (bắt PR + CI xanh) – chưa bật.
2. SigNoz: đặt retention 7 ngày (UI) nếu chưa làm. Tiếp theo `libs/platform-starter` + nối network `telemetry` vào `compose.app.yml`: log JSON ECS, `requestId` vào MDC, OTLP trace/metrics (ARCHITECTURE §5, ADR-0006).
3. Gateway P0 còn lại: access log, JWT, CORS (§4.4). 4. Phân quyền dev: user `dev`, SigNoz role Viewer, `group:dev` (§7).

**Kiểm tra nhanh trạng thái (rẻ, chạy trước khi đọc tài liệu)**
```bash
gh pr list; gh run list -L 5
ssh -o BatchMode=yes unbutu 'sudo tail -3 /opt/unbutu-platform/prod/deploy-history.log; docker compose -p prod ps --format "{{.Service}} {{.Status}}"'
```

**Bẫy đã gặp – đừng lặp lại**
- Auto mode chặn Claude **merge PR** / **`terraform apply` bootstrap** khi người dùng chưa nói rõ → `terraform plan -out=b.tfplan`, người dùng chạy `! terraform -chdir=bootstrap apply b.tfplan`. Luôn apply bootstrap **trước** khi merge PR phụ thuộc nó.
- Nhiều commit merge liên tiếp: concurrency `deploy-prod` chỉ giữ **1 deploy đang chờ duyệt** → Release cũ hiện `cancelled` là bình thường; Approve bản mới nhất.
- Không tự bấm Approve deploy (cổng duyệt của người dùng). Không chờ log dài bằng `gh run watch` – người dùng sẽ báo; kiểm tra bằng `gh run view --log-failed`.
- Lệnh tương tác (`aws configure`, `ssh-copy-id`) không chạy được qua `!` → bảo người dùng mở Terminal riêng.
- zsh: biến không tự tách từ (dùng mảng `R=(...)`, `"${R[@]}"`), `$s:l` là modifier, `echo ====` lỗi.
- Docker Desktop trên Mac hay tắt → Testcontainers fail cục bộ; để CI chạy integration test.
- Repo GitHub mới dùng **OIDC immutable subject**: `repo:<owner>@<id>/<repo>@<id>:…` (xem `gh api repos/<r>/actions/oidc/customization/sub`).
- Trong tailnet MagicDNS trả IP 100.x → kiểm tra public: `dig +short @1.1.1.1 <host>` + `curl --resolve`.
- `deploy.sh` **không** được CI cập nhật: sửa → PR **và** cài tay `sudo install -m 755 … /opt/unbutu-platform/bin/deploy.sh`.
- Không in value secret ra màn hình/log; key/secret đi thẳng qua pipe (AWS → ssh, ssh-keygen → `gh secret set`).
