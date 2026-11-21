# Runbook: dựng / vận hành SigNoz trên `unbutu` (ADR-0006)

Project `observability` **không** đi qua CI/CD: admin chạy tay trên server. Secret AWS duy nhất: token Telegram của `alert-relay` (§Alert).
File nguồn: `deploy/compose.observability.yml` + `deploy/signoz/`. Thư mục trên server: `/opt/unbutu-platform/observability`.

## 0. Điều kiện
- RAM trống ≥ 4 GiB (`free -h`). Stack dùng tối đa khoảng 3.8 GiB (ClickHouse 2g, SigNoz 512m, collector 512m, keeper 256m, 2 job một lần).
- PR chứa compose đã merge vào `main`. Lấy file **đúng commit trên `main`**, không lấy từ nhánh.

- Network `telemetry` phải tồn tại: project này tạo nó, `compose.app.yml` (prod) dùng `external: true`.
  Server mới chưa dựng SigNoz thì chạy `docker network create telemetry` trước lần deploy prod đầu tiên.

## 1. Chép cấu hình lên server (từ Mac, trong repo đã `git pull` ở `main`)
```bash
SHA=$(git rev-parse --short HEAD)
# git archive: lấy đúng nội dung đã commit. Đừng dùng tar của macOS: nó chép kèm file rác `._*` (AppleDouble).
ssh unbutu 'sudo rm -rf /opt/unbutu-platform/observability/signoz'   # xoá config cũ để không sót file đã bị xoá trong repo
git archive --format=tar HEAD deploy/compose.observability.yml deploy/signoz \
  | ssh unbutu "sudo mkdir -p /opt/unbutu-platform/observability && sudo tar xf - -C /opt/unbutu-platform/observability --strip-components=1 && echo $SHA | sudo tee /opt/unbutu-platform/observability/REVISION"
ssh unbutu 'cd /opt/unbutu-platform/observability && sudo docker compose -f compose.observability.yml config --quiet && echo CONFIG_OK'
```
Kết quả: `/opt/unbutu-platform/observability/{compose.observability.yml,signoz/…,REVISION}`.

## 2. Khởi động
```bash
ssh unbutu 'cd /opt/unbutu-platform/observability && sudo docker compose -f compose.observability.yml up -d --wait'
ssh unbutu 'sudo docker compose -p observability ps -a --format "{{.Service}} {{.Status}}"'
```
Kỳ vọng: `user-scripts` và `migrator` ở trạng thái **Exited (0)**. Keeper, ClickHouse, `signoz-signoz-0` **healthy**. `ingester`, `signoz-docker-socket-proxy` **Up**.

Kiểm tra proxy chỉ cho đọc (không dùng tên container thật khi thử POST):
```bash
ssh unbutu 'for t in "GET /containers/json" "GET /images/json" "POST /containers/khong-ton-tai/stop"; do m=${t%% *}; u=${t#* }; \
  echo "$t -> $(sudo docker run --rm --network signoz-docker-api curlimages/curl:8.11.1 -s -o /dev/null -w %{http_code} -X $m http://signoz-docker-socket-proxy:2375$u)"; done'
# kỳ vọng: 200, 403, 403
```

> **Lần dựng đầu tiên, collector chưa thu gì cho tới khi có tài khoản admin (bước 4).** Collector nhận cấu hình từ SigNoz qua OpAMP.
> Khi chưa có organization, SigNoz log `failed to find or create agent` (`orgs: 0`) và đẩy xuống pipeline `nop`.
> Collector cũng log các lỗi `opamp … connection refused` / `Server returned an error response`. Lúc này các lỗi đó là bình thường.
> Kiểm tra: `sudo docker exec signoz-ingester grep -A3 '^        logs:' /var/tmp/collector-config.yaml` phải thấy `clickhouselogsexporter`, không phải `nop`.
> Collector không có lệnh `validate`; lỗi cấu hình chỉ lộ ra trong `docker logs signoz-ingester` lúc khởi động.
Lần đầu migrator mất 1–3 phút. Nếu lỗi: `sudo docker logs signoz-telemetrystore-migrator`.

## 3. Mở UI trong tailnet (đã chốt phương án A, ADR-0006)
```bash
ssh unbutu 'curl -fsS http://127.0.0.1:3301/api/v1/health && echo'
ssh unbutu 'sudo tailscale serve --bg --https=8443 http://127.0.0.1:3301 && tailscale serve status'
```
Mở `https://unbutu.tail3c11f4.ts.net:8443` từ máy trong tailnet. `serve status` phải vẫn còn `/ → 127.0.0.1:8080 (Funnel on)` cho cổng 443,
còn cổng 8443 **không** có Funnel. Kiểm tra từ ngoài tailnet: `curl --resolve unbutu.tail3c11f4.ts.net:8443:$(dig +short @1.1.1.1 unbutu.tail3c11f4.ts.net | head -1) https://unbutu.tail3c11f4.ts.net:8443` phải **thất bại**.

## 4. Thiết lập ban đầu (làm ngay sau bước 3)
1. **Đăng ký tài khoản admin ngay**: người đăng ký đầu tiên thành Admin, nên đừng để máy khác trong tailnet đăng ký trước.
   Việc đăng ký tạo organization. Sau đó khoảng 30 giây collector nhận cấu hình thật (xem ghi chú ở bước 2).
2. Settings → General → Retention: log, trace và metrics đều **15 ngày** → **bấm Save**. Kiểm tra lại bằng query trong ClickHouse:
   `_retention_days` của `signoz_logs.logs_v2` = 15; TTL của `signoz_traces.signoz_index_v3` và `signoz_metrics.samples_v4` = `1296000` giây.
3. (Làm sau, theo §7) Tạo user `developer` với role Viewer.

## 5. Kiểm chứng log từ prod
Label log trong `compose.app.yml` chỉ có hiệu lực **sau lần deploy prod kế tiếp** (container được tạo lại).
Sau khi deploy:
```bash
curl -s https://unbutu.tail3c11f4.ts.net/api/catalog/ping
```
Vào SigNoz → Logs, lọc `service.name = catalog-service` và `deployment.environment = prod`.
`/api/catalog/ping` **không ghi log**, và collector dùng `start_at: end` nên bỏ qua log khởi động cũ. Muốn thử ngay thì sinh log giả
bằng một container chạy một lần (kết quả đã kiểm chứng ngày 2026-11-16: text → WARN; JSON → ERROR kèm trace_id):
```bash
ssh unbutu 'sudo docker run --rm --label com.docker.compose.project=prod --label com.docker.compose.service=log-test \
  --log-opt labels=com.docker.compose.project,com.docker.compose.service alpine:3.20 sh -c \
  "echo \"2026-01-01T00:00:00.000Z  WARN 1 --- [log-test] [main] demo : hello text\"; sleep 2"'
``` Log hiện tại là text, level được tách từ text.
Nếu không thấy log: `sudo docker logs signoz-ingester 2>&1 | tail -50` (lỗi operator `filelog/docker`), và kiểm tra log Docker có `attrs`:
`sudo tail -1 /var/lib/docker/containers/$(docker inspect -f '{{.Id}}' prod-catalog-service-1)/*-json.log`.

## Dashboards (lưu trong repo: `deploy/signoz/dashboards/`)
| File | Nội dung |
|---|---|
| `host-metrics.json` | CPU, RAM, load, disk IO, filesystem của `unbutu` (không có network – xem nợ kỹ thuật trong `docs/tasks/observability.md`) |
| `container-metrics.json` | CPU%, RAM%, RAM/limit, network, block IO từng container, nhóm theo `compose.project/compose.service` |
| `service-red.json` | RED theo service (rate, 5xx %, p95, p99 + vạch SLO 300 ms, bảng theo route), JVM (heap, CPU, GC, thread), HikariCP, Kafka lag. Bỏ `/actuator`. Biến `service.name` (nhiều lựa chọn). OBS-5 |

Gốc: `SigNoz/dashboards` (bản `v1`, định dạng query builder v5), đã chỉnh cho dữ liệu của server này. Import (admin, trên UI):
Dashboards → **New dashboard → Import JSON** → chọn file → **Import**. Sửa trên UI xong thì Export JSON → ghi đè file trong repo → PR,
để dashboard luôn được review như code.
**Bẫy:** dashboard mẫu của SigNoz có thể lưu sẵn giá trị biến (vd. `host.name = testingapp-server`), khiến panel trống mà không báo lỗi.
Trước khi commit, kiểm tra `variables.*.selectedValue/defaultValue` = `unbutu`. Chẩn đoán panel trống: đọc query thật trong `system.query_log` của ClickHouse.

## Alert → Telegram (OBS-5)
SigNoz chỉ có kênh Webhook (payload kiểu Alertmanager) → container `signoz-alert-relay` (`deploy/signoz/alert-relay/relay.py`,
Python thư viện chuẩn, không publish port) đổi thành tin Telegram. Token ở AWS `unbutu/prod/alert-relay` (ADR-0004).

### Dựng lần đầu (đúng thứ tự)
1. **Bot** (người dùng): Telegram → `@BotFather` → `/newbot` → lấy token. Nhắn 1 tin bất kỳ cho bot (hoặc thêm bot vào nhóm), rồi
   lấy `chat.id`: `curl -s "https://api.telegram.org/bot<TOKEN>/getUpdates" | jq '.result[].message.chat.id'`.
2. **Secret** (người dùng, Terminal riêng – `read -s` không chạy qua `!`):
   ```bash
   read -rs T; read -r C   # dán token (không hiện), rồi chat id
   jq -nc --arg t "$T" --arg c "$C" '{TELEGRAM_BOT_TOKEN:$t,TELEGRAM_CHAT_ID:$c}' \
     | aws secretsmanager create-secret --region ap-southeast-1 --name unbutu/prod/alert-relay --secret-string file:///dev/stdin
   unset T C
   ```
3. **pro-ticket-workload**: PR `prod/services/alert-relay/env.yaml` → plan xanh → merge → apply (server được quyền đọc secret).
4. **Server**: chép cấu hình (§1), sinh env, chạy riêng `alert-relay` (không đụng các container khác của stack):
   ```bash
   ssh unbutu 'sudo bash /opt/unbutu-platform/observability/signoz/alert-relay/render-env.sh'   # chỉ in TÊN biến
   ssh unbutu 'cd /opt/unbutu-platform/observability && sudo docker compose -f compose.observability.yml up -d --wait alert-relay'
   ```
   Từ giờ mọi lệnh `up` của stack cần có `env/alert-relay.env` (nằm ngoài `signoz/` nên §1 không xoá).
5. **Kênh** (SigNoz UI): Settings → Alert Channels → New → Webhook, tên `telegram`, URL `http://signoz-alert-relay:8080/alert`,
   bật "Send resolved alerts" → **Test** → phải có tin Telegram.

### Alert rule (Alerts → New Alert → Metrics, kênh `telegram`)
| Tên | Query | Điều kiện | Mức |
|---|---|---|---|
| `Lỗi 5xx > 5%` | A = `http.server.request.duration.count` rate/sum, B = như A + `http.response.status_code LIKE '5%'`, cả hai lọc `http.route NOT LIKE '/actuator%'`, group by `service.name`; công thức `B*100/A` | > 5, **all the times** trong 5 phút | critical |
| `Service mất tín hiệu` | `jvm.cpu.recent_utilization` avg/avg, group by `service.name` | "Notify when data is missing" 3 phút | critical |
| `p99 > 300 ms (SLO)` | `http.server.request.duration.bucket` p99, lọc `/actuator` như trên, group by `service.name` | > 0.3, **all the times** trong 10 phút | warning |

Summary: `{{$labels.service.name}} = {{$value}}`. Sửa trên UI xong → Export JSON → `deploy/signoz/alerts/<tên>.json` → PR.

### Kiểm chứng (diễn tập)
`ssh unbutu 'docker stop prod-catalog-service-1'` → trong ~5 phút có tin `Service mất tín hiệu` (và `Lỗi 5xx` ở api-gateway
nếu có request vào `/api/catalog`) → `docker start prod-catalog-service-1` → tin RESOLVED. Không có tin: `docker logs signoz-alert-relay`
(Telegram 401 = token sai, 400 = chat id sai), rồi SigNoz → Alerts → History.

## Đổi cấu hình ClickHouse / collector
Sửa file mount (`deploy/signoz/clickhouse/*.yaml`, `ingester/*.yaml`) **không** làm compose tạo lại container.
Sau bước 1: `sudo docker compose -f compose.observability.yml up -d --force-recreate --wait` (cả stack, không restart lẻ).

## Nâng version
Đổi `x-versions` trong compose (SigNoz cùng collector theo release notes) → PR → merge → lặp lại bước 1, rồi
`sudo docker compose -f compose.observability.yml up -d --force-recreate --wait`. Phải tạo lại cả stack, không restart lẻ từng service.

## Dừng / gỡ
```bash
ssh unbutu 'cd /opt/unbutu-platform/observability && sudo docker compose -f compose.observability.yml down'   # giữ dữ liệu
ssh unbutu 'sudo tailscale serve --https=8443 off'
```
**Không dùng `down -v`** trừ khi chủ ý xoá toàn bộ dữ liệu telemetry. Gỡ SigNoz không ảnh hưởng prod: log vẫn có trong `docker logs`.
