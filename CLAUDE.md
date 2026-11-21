# CLAUDE.md

Đọc **`HANDOFF.md`** trước (chỉ ghi việc cần làm ngay). Cuối mỗi phiên: **viết đè** `HANDOFF.md` – không nối thêm, không chép lại task đã xong. Chỉ mở **đúng mục cần** của `ARCHITECTURE.md` (~400 dòng) / `docs/setup-steps.md` / ADR khi việc đang làm đụng tới – không đọc hết để tiết kiệm token.
Task theo từng mảng: `docs/tasks/<mảng>.md` – **chỉ đọc mục "Đang mở"**, mục "Đã xong" là lưu trữ, không đọc lại.

## Tóm tắt nhanh
- Microservice Java Spring Boot, mỗi container = 1 service, Docker Compose trên 1 máy (`unbutu`, Ubuntu 24.04).
- Public: Vercel (UI) → `https://unbutu.tail3c11f4.ts.net` (Tailscale Funnel) → `127.0.0.1:8080` (api-gateway).
- Trạng thái + API đang có: `README.md`. Task theo mảng: `docs/tasks/`. Repo cấu hình prod `~/pro-ticket-workload`: AWS `038774852192`, `ap-southeast-1`.
- Truy cập server: `ssh unbutu` (qua Tailscale, có passwordless sudo). Dùng `ssh -o BatchMode=yes unbutu '...'` cho lệnh không tương tác.

## Quy tắc khi làm việc
- **Bắt đầu mỗi việc mới** (code hay tài liệu): `git checkout main && git pull --ff-only`, rồi tạo nhánh mới từ `main` (`feat/…`, `fix/…`, `docs/…`, `ci/…`) → PR → merge. Không làm tiếp trên nhánh của PR cũ (PR có thể đã merge/squash).
- PR chỉ sửa tài liệu (`*.md`, `docs/**`) hoặc config SigNoz/local không chạy CI/Release/Deploy (`paths-ignore` trong `ci.yml`), nên cứ tạo nhánh và merge thoải mái.
- `private-docs/`: ghi chép cá nhân, bị loại trong `.git/info/exclude` – **không commit/push**.
- Trên server: chỉ `api-gateway` được publish port, và chỉ `127.0.0.1:8080` (ngoại lệ: SigNoz UI `127.0.0.1:3301` → `tailscale serve :8443` – ADR-0006; kafka-ui chỉ đọc `127.0.0.1:8091` → `:9443` – ADR-0008; cả hai chỉ tailnet). Docker bỏ qua ufw – không bao giờ publish ra `0.0.0.0`. (Local: mỗi service mở port cố định trên `127.0.0.1` để debug hybrid.)
- API công khai dưới `/api/<domain>/**`; service gọi nhau bằng `http://<service-name>:8080`, URL từ biến môi trường.
- Log ra stdout dạng JSON ECS (prod), có `trace_id`/`span_id` (OTel Java agent) + `requestId` (`libs/platform-starter`; gateway: access log); service servlet mới phải phụ thuộc `libs:platform-starter`; gọi service khác chỉ qua `RestClient.Builder`/`WebClient.Builder` được inject (để propagate trace header).
- 2 môi trường: local (MacBook) và prod (server, qua CI/CD) – không có staging. 2 compose project trên server: `prod` (Funnel → 127.0.0.1:8080), `observability` (SigNoz: ClickHouse + OTel Collector – `deploy/compose.observability.yml`, admin chạy tay theo `docs/runbooks/signoz.md`, ADR-0006).
- Cấu hình + secret prod (ADR-0004): biến của service khai báo trong repo private `~/pro-ticket-workload` (`prod/services/<svc>/env.yaml`, PR → plan, merge → apply); value chỉ ở AWS Secrets Manager. Không có `.env` gõ tay, không đưa value vào git/Terraform/log. Deploy prod chỉ qua `deploy.sh` (`deploy/scripts/`).
- `pro-ticket-workload/bootstrap/` chỉ admin apply từ Mac – Claude chuẩn bị `plan -out`, người dùng tự chạy `apply`.
- Thêm service mới: thêm folder `prod/services/<service>/env.yaml` ở `pro-ticket-workload` + service vào `deploy/compose.app.yml` (`env_file: env/<service>.env`, format raw); thêm vào `deploy/compose.local.yml` (port cố định theo bảng trong README, `depends_on` chỉ hạ tầng), tạo `application-local.yml`, thêm database vào `SERVICE_DATABASES`, thêm `PORT_<service>` trong Makefile nếu chưa có.
- Làm việc với người dùng: từng bước nhỏ, giải thích mỗi bước, hỏi trước khi cài công cụ hoặc chọn hướng thiết kế.
- Không bật Tailscale SSH, không đổi ufw/Funnel/sleep config khi chưa hỏi người dùng.
- Khi hạ tầng hoặc quyết định thay đổi, cập nhật `ARCHITECTURE.md` (gồm mục "Quyết định").
- Xong một mục trong "Lộ trình senior" (§8): đánh dấu `[x]` + ngày, viết ADR `docs/adr/NNNN-<tên>.md`; sự cố/bài tập incident → `docs/postmortems/`, runbook → `docs/runbooks/`.

## Bẫy đã gặp – đừng lặp lại
- Đã báo người dùng merge PR thì **không push thêm vào nhánh đó** (#14 bị merge trước khi 2 commit sau vào kịp → mất HANDOFF). Thay đổi tiếp theo: nhánh mới từ `main`. Người dùng báo "đã merge/đã làm" → kiểm tra lại (`gh pr view`, query) trước khi làm bước sau.

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
