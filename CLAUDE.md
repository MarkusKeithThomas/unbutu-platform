# CLAUDE.md

Đọc `ARCHITECTURE.md` trước khi làm bất cứ việc gì – nó mô tả hạ tầng server `unbutu`, kiến trúc microservice và các quyết định còn mở.

## Tóm tắt nhanh
- Microservice Java Spring Boot, mỗi container = 1 service, Docker Compose trên 1 máy (`unbutu`, Ubuntu 24.04).
- Public: Vercel (UI) → `https://unbutu.tail3c11f4.ts.net` (Tailscale Funnel) → `127.0.0.1:8080` (api-gateway).
- Truy cập server: `ssh unbutu` (qua Tailscale, có passwordless sudo). Dùng `ssh -o BatchMode=yes unbutu '...'` cho lệnh không tương tác.

## Quy tắc khi làm việc
- Chỉ `api-gateway` được publish port, và chỉ `127.0.0.1:8080`. Docker bỏ qua ufw – không bao giờ publish ra `0.0.0.0`.
- API công khai dưới `/api/<domain>/**`; service gọi nhau bằng `http://<service-name>:8080`, URL từ biến môi trường.
- Log ra stdout dạng JSON ECS, luôn có `trace.id` + `requestId`; gọi service khác chỉ qua `RestClient.Builder`/`WebClient.Builder` được inject (để propagate trace header).
- 3 compose project trên server: `prod` (Funnel → 127.0.0.1:8080), `staging` (chỉ tailnet :8081), `observability` (ES + Kibana :5601 + Filebeat).
- Secret chỉ ở `/opt/unbutu-platform/.env` trên server, không commit.
- Không bật Tailscale SSH, không đổi ufw/Funnel/sleep config khi chưa hỏi người dùng.
- Khi hạ tầng hoặc quyết định thay đổi, cập nhật `ARCHITECTURE.md` (gồm mục "Quyết định").
- Xong một mục trong "Lộ trình senior" (§8): đánh dấu `[x]` + ngày, viết ADR `docs/adr/NNNN-<tên>.md`; sự cố/bài tập incident → `docs/postmortems/`, runbook → `docs/runbooks/`.
