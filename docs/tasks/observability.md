# Task: Observability (SigNoz + OpenTelemetry)

Nguồn sự thật cho mảng monitoring. Quyết định: ADR-0006 · Vận hành: `docs/runbooks/signoz.md` · Lộ trình: ARCHITECTURE §8 (Giai đoạn 0 "Observability", S1).

**Cách dùng**
- Chỉ đọc mục **Đang mở**. Mục **Đã xong** là lưu trữ 1 dòng/task, không cần đọc lại.
- Làm xong 1 task: chuyển thành 1 dòng ở **Đã xong** (ngày + PR), xoá chi tiết khỏi **Đang mở**.
- Mỗi task: 1 nhánh mới từ `main` → PR (task chỉ sửa docs hoặc `deploy/signoz/**` không chạy CI).
- Trạng thái: `TODO` · `DOING` · `BLOCKED (<lý do>)`.

## Đang mở

### OBS-5 · S1: dashboard RED + SLO + alert (= LT-1) · DOING · Claude + người dùng
- [x] Dashboard `deploy/signoz/dashboards/service-red.json` (RED, p99 + vạch SLO 300 ms, route, JVM, HikariCP, Kafka lag) – chờ import + soát trên UI.
- Alert: **đã chốt 2026-11-22 chỉ xem trên UI SigNoz** (bỏ Telegram). Còn: tạo 3 rule theo runbook `signoz.md` §Alert → export JSON vào
  `deploy/signoz/alerts/`.
- SLO: 300 ms không trùng mốc bucket (0,25 / 0,5 s) → p99 nội suy; đủ cho alert. Muốn đo đúng thì thêm mốc 0,3 qua view OTel.
- Ghi nhận: service đang chạy **Serial GC** (`MarkSweepCompact`, container thấy ít CPU) → xem lại khi load test.
- **Xong khi:** tắt catalog → alert firing trên SigNoz, bật lại → resolved; đánh dấu `[x]` S1 ở ARCHITECTURE §8 + ADR.

### OBS-6 · Quyền xem cho `developer` · BLOCKED (làm cùng phân quyền §7) · người dùng + Claude
- User SigNoz role **Viewer**. Kiểm chứng bản Community có những role nào.
- Tailscale ACL: `group:dev` → `unbutu:8443` (hiện `autogroup:member → *`).
- **Xong khi:** tài khoản dev xem được Logs/Traces nhưng không sửa được alert/cấu hình; ssh `unbutu` bị chặn.

### OBS-7 · ClickHouse ăn CPU khi rảnh · DOING (lần 2) · Claude
- Trước: ~32% CPU. **Lần 1 (#21):** tắt query profiler, `zookeeper_log`, `processors_profile_log`; async metrics 60 giây.
  Đã xác nhận ClickHouse nhận đủ các setting, và 2 bảng log đó không còn ghi. Nhưng `trace_log` vẫn khoảng 35 nghìn dòng / 2 phút.
- **Lần 2 (PR sửa):** nguyên nhân là **profiler toàn cục** (`global_profiler_*`, mặc định lấy mẫu mọi thread mỗi 10 giây) → tắt.
  Bỏ thêm `session_log`, `query_views_log`.
- **Xong khi:** sau khi tạo lại stack và **chờ ≥ 30 phút** (lúc mới khởi động còn merge nền), CPU lúc rảnh < 5%, hoặc ghi lại mức đo được.
  Lưu ý: giờ có thêm trace/metrics thật từ 2 service nên tải nền cao hơn trước.

## Nợ kỹ thuật đã biết (chấp nhận, không phải task)
- Collector không có extension `file_storage` → `start_at: end`. Log sinh ra lúc collector tắt không vào SigNoz (vẫn có trong `docker logs`).
- Collector chạy uid 0 (file log Docker là `root 0640`) nhưng `cap_drop: ALL` (`CapEff` = 0, không đọc được `/proc/1/environ`), mount read-only.
- Không có metrics network của host: scraper `network` sẽ đọc network namespace của collector. Network từng container thì có (`container.network.*`).
- Socket proxy cho `GET /containers/<id>/json`, nên collector đọc được **Env của container prod** (mật khẩu DB). Đã kiểm tra proxy chặn: `/images`, `/info`, mọi POST → 403. Chặn hẳn inspect cần rule haproxy riêng.
- Collector không có lệnh `validate`; lỗi cấu hình chỉ lộ ra trong `docker logs signoz-ingester`.
- SigNoz ghim `v0.148.1`. v0.149 ghi log 2 lần, nên đọc release notes trước khi nâng.

## Đã xong
- 2026-11-16 · OBS-3 + OBS-4: OTel Java agent (chỉ prod) + access log gateway/catalog + `requestId` MDC; trace waterfall 7 span gateway→catalog→JDBC, log 2 service cùng `trace_id`/`requestId`, metrics `http.server.request.duration`/`jvm.*`/`db.client.connections.*`, không còn span `/actuator`, attribute log phẳng (`url.path`, `http.response.status_code` kiểu số), 1 header `X-Request-Id`; RAM gateway 302 MB, catalog 355 MB (+~80–100 MB do agent, < 512m) · PR #21, #22
- 2026-11-16 · Dashboard host + container dạng code (`deploy/signoz/dashboards/`), sửa `host.name` mặc định = `unbutu` · PR #18, #19
- 2026-11-16 · OBS-2 metrics host (17 metric `system.*`) + container (13 metric `container.*`, 8 container, nhãn `compose.service`) qua socket proxy chỉ-đọc; `filesystem /` và RAM khớp `df`/`free`; collector 51 MB, proxy 19 MB · PR #15
- 2026-11-16 · OBS-1 retention **15 ngày** cho log/trace/metrics (người dùng chọn 15 thay vì 7), đã kiểm tra trong ClickHouse; tick mục Observability ở §8
- 2026-11-16 · Chốt SigNoz thay ELK + Prometheus/Grafana (ADR-0006) · PR #10
- 2026-11-16 · Compose `observability` + config collector/ClickHouse + runbook + label log prod · PR #11
- 2026-11-16 · Dựng trên `unbutu` (bản `999200b`), UI `:8443` qua `tailscale serve` (chỉ tailnet), tạo admin, log thử vào được ClickHouse
- 2026-11-16 · Runbook sửa theo lần dựng thật + `paths-ignore` cho thay đổi chỉ có docs · PR #12
