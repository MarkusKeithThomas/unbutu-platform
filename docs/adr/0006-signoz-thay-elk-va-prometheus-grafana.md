# ADR-0006: SigNoz (OpenTelemetry) thay ELK + Prometheus/Grafana

- Trạng thái: Chấp nhận · 2026-11-16
- Liên quan: ADR-0001 (Compose, project `observability`), ADR-0003 (chỉ prod trên server)
- Thay thế: phần "Log tập trung: Elasticsearch + Kibana + Filebeat" (§12) và công cụ của S1 (Prometheus, Grafana, Alertmanager) trong `ARCHITECTURE.md`

## Bối cảnh
Kế hoạch cũ dùng 2 bộ công cụ: ELK (log, ~3 GB RAM) và Prometheus + Grafana + Alertmanager (metrics/alert, ~400 MB).
Distributed tracing dạng waterfall (Jaeger/Tempo) bị hoãn vì thiếu RAM. Lúc ra quyết định thì project `observability`
**chưa được dựng** trên server, nên chưa tốn công chuyển đổi.

Công ty của người dùng đã chuyển sang **SigNoz** và bỏ Grafana, Kibana, Elasticsearch. Mục tiêu của project là luyện kỹ năng
dùng được ngay ở nơi làm việc (§3), vì vậy nên dùng đúng công cụ đó.

Server còn trống khoảng 10 GiB RAM (đo ngày 2026-11-16: prod dùng khoảng 1.3 GiB).

## Quyết định
1. **Backend observability là SigNoz bản Community, tự host** trong compose project `observability`
   (ClickHouse + OTel Collector + SigNoz UI/query). SigNoz lo cả log, metrics, trace, dashboard và alert. Không mất tiền license.
2. **Đo đạc theo chuẩn OpenTelemetry (OTLP).** Service không phụ thuộc SigNoz: muốn đổi backend thì chỉ đổi exporter ở collector.
3. **Log**: giữ quy ước hiện có. Service ghi ECS JSON ra stdout (có `trace.id`, `requestId`); OTel Collector đọc
   log Docker và đẩy vào SigNoz. Không bắt app tự đẩy log qua mạng, để log vẫn đọc được bằng `docker logs` khi SigNoz chết.
4. **Trace + metrics**: service đẩy OTLP tới collector qua Docker network (`http://<collector>:4318`), URL lấy từ biến môi trường.
   Header `traceparent` + `X-Request-Id` vẫn propagate như §5.2.
5. **Giữ dữ liệu 7 ngày** (TTL ClickHouse, cấu hình trong SigNoz) cho log, trace và metrics. Mỗi container có `mem_limit`.
6. **Truy cập UI chỉ trong tailnet** (chốt 2026-11-16): Docker publish `127.0.0.1:3301` (không dùng 8080 vì trùng gateway),
   rồi `tailscale serve --bg --https=8443 http://127.0.0.1:3301` → `https://unbutu.tail3c11f4.ts.net:8443`, **không Funnel**.
   Bỏ phương án bind IP Tailscale: nếu Docker khởi động trước tailscaled thì IP 100.x chưa có và container không mở được port. Cách mới còn có HTTPS.
7. **Phân quyền**: dùng role có sẵn của SigNoz (Admin/Editor/Viewer). Role `developer` (§7) là Viewer. Phải kiểm chứng
   những gì bản Community hỗ trợ khi dựng.

## Triển khai (2026-11-16)
- SigNoz đã bỏ bộ compose trong repo chính, chuyển sang **Foundry** (`foundryctl` sinh compose từ `casting.yaml`). Ở đây **không cài Foundry**:
  lấy output mẫu của Foundry v0.4.0, chỉnh tay, rồi commit vào `deploy/compose.observability.yml` + `deploy/signoz/` để review được trong PR.
- Phiên bản ghim: `signoz/signoz:v0.148.1`, `signoz/signoz-otel-collector:v0.144.15`, ClickHouse/Keeper `25.12.5`.
  Không lên v0.149: bản đó ghi log body 2 lần (chuyển sang JSON native), tốn thêm CPU, RAM và ổ đĩa.
- Metastore dùng **SQLite** trong volume (Foundry mặc định dùng thêm 1 container Postgres) để tiết kiệm RAM.
- Log: collector đọc `/var/lib/docker/containers` (read-only, **chạy root** vì file là `root 0640`). `compose.app.yml` gắn label
  `com.docker.compose.project/service` vào từng dòng log; collector chỉ giữ project `prod`. Collector không có `file_storage`,
  nên đặt `start_at: end`: log sinh ra lúc collector tắt sẽ không vào SigNoz (vẫn xem được bằng `docker logs`).
- Tắt `statsreporter` (mặc định gửi thống kê **kèm email** về SigNoz) và `analytics`.
- OTLP không publish ra máy; service prod gửi qua network Docker `telemetry` (alias `otel-collector`), sẽ nối khi gắn OTel.

**Còn mở** (chốt khi gắn vào service): đo đạc bằng **Micrometer Tracing/Metrics + OTLP exporter** (nhẹ, theo hướng Spring Boot)
hay dùng **OpenTelemetry Java agent** (tự đo nhiều thư viện, tốn thêm khoảng 50–100 MB mỗi JVM, khởi động chậm hơn).

## Phương án đã loại
- **ELK + Prometheus/Grafana (kế hoạch cũ)**: 2 hệ, 2 UI, vẫn thiếu tracing. Tốn RAM tương đương hoặc hơn. Không khớp với công cụ ở công ty.
- **Grafana LGTM (Loki, Tempo, Mimir/Prometheus, Grafana)**: phổ biến trên thị trường nhưng phải vận hành 4–5 thành phần.
  Công ty đã bỏ Grafana. Sau này vẫn có thể đẩy cùng dữ liệu OTel sang đây nhờ quyết định 2.
- **SaaS (SigNoz Cloud, Datadog…)**: tốn tiền, và không luyện được kỹ năng tự vận hành.

## Hệ quả
- (+) Gộp mục "Observability" của giai đoạn 0 với S1: một hệ cho log, metrics, trace và alert. S1 không cần thêm khoảng 400 MB cho Prometheus/Grafana.
- (+) Có trace waterfall ngay (gateway → service → DB), thay cho việc chỉ lọc log theo `traceId`. §11 bỏ mục "thêm Jaeger/Tempo".
- (+) Kiến thức OpenTelemetry dùng được với mọi backend.
- (−) ClickHouse tốn RAM khi query nặng, phải giới hạn bộ nhớ. Tổng observability ước khoảng 3 GB, ngang ELK.
- (−) Ít xuất hiện trong JD hơn ELK/Grafana. Có thể bù sau bằng bài tập phụ: export OTel sang Prometheus.
- (−) Gửi alert tới Telegram: SigNoz có kênh webhook, nhưng gửi thẳng Telegram được không thì kiểm chứng khi làm S1.
- (−) ADR-0003 có nhắc "Kibana chỉ đọc". Từ nay hiểu là "SigNoz Viewer".
