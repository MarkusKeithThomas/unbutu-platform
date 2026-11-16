# Task: Observability (SigNoz + OpenTelemetry)

Nguồn sự thật cho mảng monitoring. Quyết định: ADR-0006 · Vận hành: `docs/runbooks/signoz.md` · Lộ trình: ARCHITECTURE §8 (Giai đoạn 0 "Observability", S1).

**Cách dùng**
- Chỉ đọc mục **Đang mở**. Mục **Đã xong** là lưu trữ 1 dòng/task, không cần đọc lại.
- Làm xong 1 task: chuyển thành 1 dòng ở **Đã xong** (ngày + PR), xoá chi tiết khỏi **Đang mở**.
- Mỗi task: 1 nhánh mới từ `main` → PR (task chỉ sửa docs hoặc `deploy/signoz/**` không chạy CI).
- Trạng thái: `TODO` · `DOING` · `BLOCKED (<lý do>)`.

## Đang mở

### OBS-3 + OBS-4 · Access log + OpenTelemetry Java agent · DOING (code xong trong PR, chờ merge → deploy → kiểm chứng) · Claude
- Đã chốt (người dùng, 2026-11-16): **OpenTelemetry Java agent** `v2.33.1`, chỉ bật ở prod (ADR-0006 §"Đo đạc service").
- Đã code:
  - Gateway `AccessLogWebFilter` (bỏ `/actuator`, IP thật lấy từ `X-Forwarded-For` khi đi qua Funnel).
  - `libs/platform-starter`: `RequestIdFilter` → MDC, `AccessLogFilter`, `RestClient` truyền `X-Request-Id`; catalog dùng starter.
  - Dockerfile `ADD --checksum` agent. `compose.app.yml`: `JDK_JAVA_OPTIONS`, `OTEL_*`, `LOGGING_STRUCTURED_*`, network `telemetry` (external).
  - Collector: đọc log ECS lồng nhau + `trace_id`/`span_id`, bỏ span `/actuator`.
- Đã kiểm ở máy: gateway + agent → access log JSON có `trace_id` trùng span SERVER, `requestId`, `client.address`. Test đều qua.
- **Chưa kiểm được trước merge:** cách SigNoz lưu attribute lồng nhau (`http.*`, `url.*`, `log.logger`) – lệnh chạy collector
  tạm ghi vào ClickHouse bị auto mode chặn. Sau deploy: kiểm tra key trong `attributes_string`/`attributes_number`.
  Nếu bị lưu thành chuỗi JSON thì thêm bước làm phẳng trong `ingester.yaml`.
- **Sau merge, theo thứ tự:**
  1. Dựng lại observability: runbook bước 1, rồi `up -d --force-recreate --wait` cả stack (vì đổi config ClickHouse + collector).
  2. Approve deploy prod (image mới có agent).
  3. `curl -H 'X-Request-Id: obs4-check' https://unbutu.tail3c11f4.ts.net/api/catalog/events`.
- **Xong khi:**
  - SigNoz Traces có waterfall `api-gateway` → `catalog-service` (+ span JDBC).
  - Logs của cả 2 service lọc được theo cùng `trace_id` và `requestId = obs4-check`.
  - Metrics JVM/HTTP (`jvm.*`, `http.server.request.duration`) có `service.name`.
  - Không có trace `/actuator`.
  - RAM của gateway/catalog vẫn < 512m (xem dashboard container).

### OBS-5 · S1: dashboard RED + SLO + alert · BLOCKED (cần OBS-2, OBS-4) · Claude + người dùng
- Dashboard RED (rate, errors, duration) cho từng service + JVM. SLO: 99% request < 300 ms.
- Alert: error rate > 5% hoặc service down → Telegram. **Kiểm chứng:** kênh webhook của SigNoz gửi thẳng Telegram Bot API được
  không, hay cần cầu nối. Token bot để trên AWS (ADR-0004), không đưa vào git.
- Dashboard/alert lưu dạng JSON trong repo (`deploy/signoz/dashboards/`) để review được.
  Đã có `host-metrics.json` + `container-metrics.json` (đã import, có data). Còn thiếu: RED theo service (cần OBS-4), SLO, alert.
- **Xong khi:** gây lỗi thử (vd. tắt catalog) → nhận được tin Telegram; đánh dấu `[x]` S1 ở ARCHITECTURE §8 + ADR.

### OBS-6 · Quyền xem cho `developer` · BLOCKED (làm cùng phân quyền §7) · người dùng + Claude
- User SigNoz role **Viewer**. Kiểm chứng bản Community có những role nào.
- Tailscale ACL: `group:dev` → `unbutu:8443` (hiện `autogroup:member → *`).
- **Xong khi:** tài khoản dev xem được Logs/Traces nhưng không sửa được alert/cấu hình; ssh `unbutu` bị chặn.

### OBS-7 · ClickHouse ăn CPU khi rảnh · DOING (cùng PR với OBS-3/4) · Claude
- Đo 2026-11-16: ~32% CPU, 600 MB. Query + merge chỉ khoảng 50 giây CPU / 30 phút (khoảng 3% một nhân).
  Phần còn lại là ClickHouse tự giám sát: `system` tạo 1041 part mới / 30 phút (SigNoz: 298); `trace_log` 1.35 triệu dòng
  (query profiler); `zookeeper_log` 730 nghìn dòng.
- Đã sửa `deploy/signoz/clickhouse/config.yaml`: tắt query profiler, `zookeeper_log`, `processors_profile_log`;
  `asynchronous_metrics_update_period_s` 60. **Giữ** `query_log` + `part_log` (dùng để chẩn đoán).
- **Xong khi:** sau khi tạo lại stack, CPU của ClickHouse lúc rảnh < 5%, hoặc ghi lại mức đo được và lý do chấp nhận.
  Xem trên dashboard container `observability/signoz-telemetrystore-clickhouse-0-0`.

## Nợ kỹ thuật đã biết (chấp nhận, không phải task)
- Collector không có extension `file_storage` → `start_at: end`. Log sinh ra lúc collector tắt không vào SigNoz (vẫn có trong `docker logs`).
- Collector chạy uid 0 (file log Docker là `root 0640`) nhưng `cap_drop: ALL` (`CapEff` = 0, không đọc được `/proc/1/environ`), mount read-only.
- Không có metrics network của host: scraper `network` sẽ đọc network namespace của collector. Network từng container thì có (`container.network.*`).
- Socket proxy cho `GET /containers/<id>/json`, nên collector đọc được **Env của container prod** (mật khẩu DB). Đã kiểm tra proxy chặn: `/images`, `/info`, mọi POST → 403. Chặn hẳn inspect cần rule haproxy riêng.
- Collector không có lệnh `validate`; lỗi cấu hình chỉ lộ ra trong `docker logs signoz-ingester`.
- SigNoz ghim `v0.148.1`. v0.149 ghi log 2 lần, nên đọc release notes trước khi nâng.

## Đã xong
- 2026-11-16 · Dashboard host + container dạng code (`deploy/signoz/dashboards/`), sửa `host.name` mặc định = `unbutu` · PR #18, #19
- 2026-11-16 · OBS-2 metrics host (17 metric `system.*`) + container (13 metric `container.*`, 8 container, nhãn `compose.service`) qua socket proxy chỉ-đọc; `filesystem /` và RAM khớp `df`/`free`; collector 51 MB, proxy 19 MB · PR #15
- 2026-11-16 · OBS-1 retention **15 ngày** cho log/trace/metrics (người dùng chọn 15 thay vì 7), đã kiểm tra trong ClickHouse; tick mục Observability ở §8
- 2026-11-16 · Chốt SigNoz thay ELK + Prometheus/Grafana (ADR-0006) · PR #10
- 2026-11-16 · Compose `observability` + config collector/ClickHouse + runbook + label log prod · PR #11
- 2026-11-16 · Dựng trên `unbutu` (bản `999200b`), UI `:8443` qua `tailscale serve` (chỉ tailnet), tạo admin, log thử vào được ClickHouse
- 2026-11-16 · Runbook sửa theo lần dựng thật + `paths-ignore` cho thay đổi chỉ có docs · PR #12
