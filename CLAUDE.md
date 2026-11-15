# CLAUDE.md

Đọc `ARCHITECTURE.md` trước khi làm bất cứ việc gì – nó mô tả hạ tầng server `unbutu`, kiến trúc microservice và các quyết định còn mở.

## Tóm tắt nhanh
- Microservice Java Spring Boot, mỗi container = 1 service, Docker Compose trên 1 máy (`unbutu`, Ubuntu 24.04).
- Public: Vercel (UI) → `https://unbutu.tail3c11f4.ts.net` (Tailscale Funnel) → `127.0.0.1:8080` (api-gateway).
- Truy cập server: `ssh unbutu` (qua Tailscale, có passwordless sudo). Dùng `ssh -o BatchMode=yes unbutu '...'` cho lệnh không tương tác.

## Quy tắc khi làm việc
- Trên server: chỉ `api-gateway` được publish port, và chỉ `127.0.0.1:8080`. Docker bỏ qua ufw – không bao giờ publish ra `0.0.0.0`. (Local: mỗi service mở port cố định trên `127.0.0.1` để debug hybrid.)
- API công khai dưới `/api/<domain>/**`; service gọi nhau bằng `http://<service-name>:8080`, URL từ biến môi trường.
- Log ra stdout dạng JSON ECS, luôn có `trace.id` + `requestId`; gọi service khác chỉ qua `RestClient.Builder`/`WebClient.Builder` được inject (để propagate trace header).
- 2 môi trường: local (MacBook) và prod (server, qua CI/CD) – không có staging. 2 compose project trên server: `prod` (Funnel → 127.0.0.1:8080), `observability` (ES + Kibana :5601 + Filebeat).
- Cấu hình + secret prod (ADR-0004): biến của service khai báo trong repo private `~/pro-ticket-workload` (`prod/services/<svc>/env.yaml`, PR → plan, merge → apply); value chỉ ở AWS Secrets Manager. Không có `.env` gõ tay, không đưa value vào git/Terraform/log. Deploy prod chỉ qua `deploy.sh` (`deploy/scripts/`).
- `pro-ticket-workload/bootstrap/` chỉ admin apply từ Mac – Claude chuẩn bị `plan -out`, người dùng tự chạy `apply`.
- Thêm service mới: thêm folder `prod/services/<service>/env.yaml` ở `pro-ticket-workload` + service vào `deploy/compose.app.yml` (`env_file: env/<service>.env`, format raw); thêm vào `deploy/compose.local.yml` (port cố định theo bảng trong README, `depends_on` chỉ hạ tầng), tạo `application-local.yml`, thêm database vào `SERVICE_DATABASES`, thêm `PORT_<service>` trong Makefile nếu chưa có.
- Làm việc với người dùng: từng bước nhỏ, giải thích mỗi bước, hỏi trước khi cài công cụ hoặc chọn hướng thiết kế.
- Không bật Tailscale SSH, không đổi ufw/Funnel/sleep config khi chưa hỏi người dùng.
- Khi hạ tầng hoặc quyết định thay đổi, cập nhật `ARCHITECTURE.md` (gồm mục "Quyết định").
- Xong một mục trong "Lộ trình senior" (§8): đánh dấu `[x]` + ngày, viết ADR `docs/adr/NNNN-<tên>.md`; sự cố/bài tập incident → `docs/postmortems/`, runbook → `docs/runbooks/`.
