# Task: Observability (SigNoz + OpenTelemetry)

Nguồn sự thật cho mảng monitoring. Quyết định: ADR-0006 · Vận hành: `docs/runbooks/signoz.md` · Lộ trình: ARCHITECTURE §8 (Giai đoạn 0 "Observability", S1).

**Cách dùng**
- Chỉ đọc mục **Đang mở**. Mục **Đã xong** là lưu trữ 1 dòng/task, không cần đọc lại.
- Làm xong 1 task: chuyển thành 1 dòng ở **Đã xong** (ngày + PR), xoá chi tiết khỏi **Đang mở**.
- Mỗi task: 1 nhánh mới từ `main` → PR (task chỉ sửa docs hoặc `deploy/signoz/**` không chạy CI).
- Trạng thái: `TODO` · `DOING` · `BLOCKED (<lý do>)`.

## Đang mở

### OBS-3 · Gateway access log · TODO · Claude
- Hạng mục P0 trong ARCHITECTURE §4.4. Global filter ghi 1 dòng/request: method, path, status, latency, client IP, `requestId`, traceId.
- Dùng chung với OBS-4 nếu làm sau (lúc đó log theo định dạng ECS).
- **Xong khi:** gọi `/api/catalog/ping` → SigNoz Logs thấy dòng của `api-gateway` có `requestId` trùng header `X-Request-Id` trả về.

### OBS-4 · `libs/platform-starter` + OpenTelemetry cho service · BLOCKED (người dùng chốt cách đo đạc) · Claude
- **Quyết định cần chốt:** Micrometer Tracing/Metrics + OTLP exporter (đề xuất: nhẹ, theo hướng Spring Boot)
  hay OpenTelemetry Java agent (+50–100 MB mỗi JVM). Chốt xong thì ghi vào ADR-0006 §"Còn mở" và ARCHITECTURE §12.
- Các việc:
  1. Log JSON ECS ra stdout, có `trace.id`/`span.id` + `requestId` (MDC); `service.name`, `service.version` = `APP_VERSION`.
  2. Filter `X-Request-Id` → MDC + baggage; propagate qua `RestClient.Builder`/`WebClient.Builder` được inject.
  3. Export OTLP: `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318`. Khai báo trong `pro-ticket-workload`
     (`prod/services/<svc>/env.yaml`, không phải secret) và `application-local.yml`.
  4. `deploy/compose.app.yml`: các service nối network `telemetry` (`external: true`). **Rủi ro:** nếu stack observability
     chưa chạy thì network chưa tồn tại và deploy prod lỗi. Cần xử lý, ví dụ `deploy.sh` tạo network nếu thiếu.
  5. `deploy/signoz/ingester/ingester.yaml`: khi định dạng log đã chốt, xoá các nhánh parse thừa
     (`sev_flat`/`sev_nested`, `trace_ecs`/`trace_mdc`; có TODO trong file).
  6. Local: chạy collector trong `compose.local.yml` hay tắt export (chọn khi làm).
- **Xong khi:** 1 request qua gateway → catalog có **trace waterfall** trong SigNoz Traces; log của cả 2 service lọc được
  theo cùng `trace_id`/`requestId`; metrics JVM + HTTP hiện trong SigNoz.

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

### OBS-7 · ClickHouse ăn CPU khi rảnh · TODO · Claude
- Đo 2026-11-16: ClickHouse ~15% CPU, ~490 MB RAM khi gần như không có dữ liệu (stack mới chạy 18 phút).
  Sau OBS-2 (thêm metrics host + container mỗi 30 giây): ~23% CPU, ~550 MB RAM.
  Giờ có thể xem trên SigNoz: `container.cpu.*` với `compose.service = signoz-telemetrystore-clickhouse-0-0`.
- Đo lại sau vài giờ: `ssh unbutu 'docker stats --no-stream | grep clickhouse'`. Nếu vẫn > 5%: giảm system log
  (`metric_log`, `trace_log`, `asynchronous_metric_log`, `query_log`…) trong `deploy/signoz/clickhouse/config.yaml`.
- **Xong khi:** CPU lúc rảnh < 5%, hoặc ghi lại lý do chấp nhận.

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
