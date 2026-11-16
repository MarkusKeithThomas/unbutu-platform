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
6. **Truy cập UI chỉ trong tailnet**: publish port bind vào IP Tailscale của `unbutu`, **không bao giờ `0.0.0.0`**
   (Docker bỏ qua ufw). Đây là ngoại lệ thứ 2 sau `api-gateway`. Port cụ thể chốt khi dựng, nhưng không được dùng 8080 vì trùng gateway.
7. **Phân quyền**: dùng role có sẵn của SigNoz (Admin/Editor/Viewer). Role `developer` (§7) là Viewer. Phải kiểm chứng
   những gì bản Community hỗ trợ khi dựng.

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
