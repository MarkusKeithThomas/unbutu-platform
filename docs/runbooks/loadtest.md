# Runbook: load test bằng k6 (chạy theo bậc + dashboard trực tiếp)

Công cụ: [k6](https://grafana.com/docs/k6/) `grafana/k6:1.8.2` (chạy bằng Docker, không cài gì lên Mac/server).
File: `loadtest/run.sh` (gọi qua `make loadtest`), kịch bản `loadtest/k6/<kịch bản>.js`, logic bậc + bảng kết quả `loadtest/k6/lib/steps.js`.

## Load test ở đây đo gì
Bắn **đúng N request/giây** (rps) trong một khoảng thời gian (1 **bậc**), tăng dần qua nhiều bậc. Mỗi bậc được chấm:
- **p95 < 300 ms**: 95% request trả lời dưới 300 ms (`P95_MS`).
- **lỗi < 1%**: HTTP ≥ 400 hoặc lỗi mạng (`MAX_ERR_PCT`).
- **bỏ lượt < 5%**: k6 hết người dùng ảo để gửi đúng nhịp → hệ thống phản hồi không kịp.

Kết quả: **"PASS tới X rps"** = bậc cao nhất mà mọi bậc trước nó đều PASS. Mặc định dừng ngay khi 1 bậc FAIL (`STOP_ON_FAIL=true`).

## Chạy bằng nút bấm trên web (`/admin/loadtest`, chỉ ADMIN)
Prod: `https://unbutu.tail3c11f4.ts.net/admin/loadtest` (hoặc qua UI Vercel) · local: `http://localhost:8080/admin/loadtest` (`make up`).
1. Chọn kịch bản, các bậc rps (vd. `100,200,400,800`), thời gian mỗi bậc, ngưỡng p95/lỗi → **Chạy load test** → xác nhận.
2. Trang tự cập nhật mỗi 2 s: rps thực tế vs mục tiêu, p95 (dồn từ đầu bậc) với vạch ngưỡng, bảng từng bậc, ô **"Chịu được X rps"**.
3. **Dừng lượt đang chạy** bất cứ lúc nào (k6 vẫn chấm điểm các bậc đã chạy). Lịch sử: bảng cuối trang → **Xem**.

Cách hoạt động: `loadtest-service` (image có binary k6) chạy `k6 run` như tiến trình con, bắn `http://api-gateway:8080`;
đọc `GET /v1/metrics` của k6 (127.0.0.1:6565 trong container) mỗi 2 s; dừng = `PATCH /v1/status`; kết thúc → đọc bảng bậc
(`OUT_DIR/result.json` do `handleSummary` ghi) → lưu `loadtest_db.runs`. An toàn: 1 lượt/lúc (409), trần `LOADTEST_MAX_RPS`
(prod 2000, local 300), tổng ≤ `LOADTEST_MAX_DURATION` (30′), container `cpus: 1`. Service restart giữa chừng → lượt thành `ABORTED`.
Giới hạn: p99 không có trong số liệu trực tiếp (REST API của k6 chỉ trả p90/p95) – có trong bảng cuối.

## Chạy bằng lệnh (`make loadtest`)
```bash
make loadtest                                        # prod, kịch bản browse, bậc 50,100,200 rps, mỗi bậc 30s
make loadtest STEPS=100,200,400,800 STEP_TIME=30s    # tự chọn bậc
make loadtest STEPS=200,250,300,350                  # dò kỹ quanh điểm gãy
make loadtest TARGET=local STEPS=20,50               # stack local (make up trước)
```
Tham số khác: `P95_MS=500`, `MAX_ERR_PCT=0.5`, `STOP_ON_FAIL=false` (chạy hết các bậc), `MAX_RPS` (trần an toàn, mặc định 2000).

1. Lệnh in `Dashboard trực tiếp: http://localhost:5665` → mở trình duyệt khi k6 bắt đầu chạy.
   Dashboard: rps, độ trễ (p90/p95/p99), % lỗi, số người dùng ảo theo thời gian.
2. Kết thúc (hoặc **Ctrl+C** để dừng sớm) → terminal in bảng:
   ```
   Bậc       đạt rps   p95       p99       lỗi     bỏ lượt  kết quả
   50 rps    50        41 ms     60 ms     0.00%   0        PASS
   100 rps   100       55 ms     90 ms     0.00%   0        PASS
   200 rps   171       640 ms    1210 ms   0.00%   1450     FAIL
   KẾT QUẢ: PASS tới 100 rps (bậc 200 rps FAIL)
   ```
3. Báo cáo lưu ở `loadtest/reports/<kịch bản>-<thời gian>.html` (mở bằng trình duyệt, giống dashboard) và `.json` (bảng bậc).
   Thư mục này không commit; kết quả đáng giữ → ghi vào `docs/loadtest/`.

## Kịch bản
| Tên | Làm gì | Ghi chú |
|---|---|---|
| `browse` | 40% danh sách sự kiện · 20% chi tiết · 20% hạng vé · 20% "Còn x/y" (inventory, Redis) | Chỉ đọc – an toàn cho prod. Cần ≥ 1 sự kiện đang bán |
| `purchase` | **Mua vé end-to-end**: giữ vé → checkout → ngân hàng giả lập "thành công" → IPN → Kafka → CONFIRMED → hỏi `/api/tickets/bookings/{id}` tới khi có QR. 1 lượt = 1 vé ≈ 6 request | **Ghi dữ liệu** – chỉ qua nút bấm web (cần dữ liệu do loadtest-service chuẩn bị). Bậc tính theo **lượt mua/giây**; PASS thêm: ≥ 99% đơn có QR, p95 "tới lúc có QR" < ô "Tới lúc có QR tối đa" (mặc định 5000 ms). Bậc FAIL ghi rõ tiêu chí trượt (p95 · lỗi · bỏ lượt · tới lúc có QR · % có vé). Tối đa 14′/lượt (token tài khoản test 15′) |

### Kịch bản `purchase` – dữ liệu test
Bấm chạy → loadtest-service (bằng token của ADMIN đang bấm):
1. Tạo **sự kiện ẩn mới** "Load Test dd/MM HH:mm" (catalog `hidden=true` – không hiện trong danh sách khách, mở/mua được qua id)
   + 1 hạng vé ON_SALE kho 1.000.000.
2. Bật **cờ bỏ giới hạn 4 vé/tài khoản** cho đúng sự kiện đó: `PUT /api/bookings/admin/limit-bypass/{eventId}?minutes=N`
   (bỏ cả khoá (tài khoản, sự kiện) để 1 tài khoản mua song song). Có hạn (≤ 120′) → service chết giữa chừng thì cờ tự tắt.
   Sự kiện khác vẫn giữ giới hạn 4 vé. Xem cờ đang bật: `GET /api/bookings/admin/limit-bypass` (ADMIN).
3. Tài khoản test `loadtest-buyer@unbutu.test` (mật khẩu: `LOADTEST_ACCOUNT_PASSWORD`, prod ở secret `unbutu/prod/loadtest-service`) – tự đăng ký nếu chưa có.

Xong lượt: tắt cờ + hạng vé → PAUSED. Đơn/giao dịch/vé test **ở lại DB**, gắn `event_id` của sự kiện ẩn (cột `event_id` trong
`loadtest_db.runs`). Biết trước: ngân hàng giả lập gửi IPN tuần tự ≤ 50/giây (`MockIpnSender.claimDue(50)` mỗi 1 s) → trần giả
~50 lượt mua/giây cho tới khi sửa (VNPay thật gửi song song).

## Cách chạy trên prod & giới hạn của phép đo
- k6 chạy **trên chính server** trong mạng `prod_default`, gọi thẳng `http://api-gateway:8080` → đo năng lực hệ thống,
  không đo Internet/Funnel/CDN của người dùng thật.
- k6 bị giới hạn `--cpus 1 --memory 1g` nhưng vẫn **tranh CPU** với service bị đo (máy 4 core) → con số là cận dưới.
- Dashboard: container publish `127.0.0.1:5665` trên server **chỉ trong lúc chạy** (`--rm`), Mac xem qua `ssh -L` – không qua Funnel/tailscale serve.
- Mỗi lúc 1 lượt: container tên cố định `k6-loadtest` (chạy lượt thứ 2 → lỗi "name already in use").
- Đang test mà prod chậm/lỗi với người dùng thật → **Ctrl+C**; còn kẹt: `ssh unbutu 'docker rm -f k6-loadtest'`.
- Tìm nguyên nhân khi FAIL: SigNoz dashboard **unbutu – Service RED + JVM** (p99 theo service, pool DB, GC) + **Container** (CPU/RAM).
