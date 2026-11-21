# Runbook: load test bằng k6 (chạy theo bậc + dashboard trực tiếp)

Công cụ: [k6](https://grafana.com/docs/k6/) `grafana/k6:1.8.2` (chạy bằng Docker, không cài gì lên Mac/server).
File: `loadtest/run.sh` (gọi qua `make loadtest`), kịch bản `loadtest/k6/<kịch bản>.js`, logic bậc + bảng kết quả `loadtest/k6/lib/steps.js`.

## Load test ở đây đo gì
Bắn **đúng N request/giây** (rps) trong một khoảng thời gian (1 **bậc**), tăng dần qua nhiều bậc. Mỗi bậc được chấm:
- **p95 < 300 ms**: 95% request trả lời dưới 300 ms (`P95_MS`).
- **lỗi < 1%**: HTTP ≥ 400 hoặc lỗi mạng (`MAX_ERR_PCT`).
- **bỏ lượt < 5%**: k6 hết người dùng ảo để gửi đúng nhịp → hệ thống phản hồi không kịp.

Kết quả: **"PASS tới X rps"** = bậc cao nhất mà mọi bậc trước nó đều PASS. Mặc định dừng ngay khi 1 bậc FAIL (`STOP_ON_FAIL=true`).

## Chạy
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
| (sau) `hold`, `checkout` | Giữ vé / mua đủ luồng | Cần tài khoản + sự kiện test + dọn dữ liệu – `docs/tasks/loadtest.md` |

## Cách chạy trên prod & giới hạn của phép đo
- k6 chạy **trên chính server** trong mạng `prod_default`, gọi thẳng `http://api-gateway:8080` → đo năng lực hệ thống,
  không đo Internet/Funnel/CDN của người dùng thật.
- k6 bị giới hạn `--cpus 1 --memory 1g` nhưng vẫn **tranh CPU** với service bị đo (máy 4 core) → con số là cận dưới.
- Dashboard: container publish `127.0.0.1:5665` trên server **chỉ trong lúc chạy** (`--rm`), Mac xem qua `ssh -L` – không qua Funnel/tailscale serve.
- Mỗi lúc 1 lượt: container tên cố định `k6-loadtest` (chạy lượt thứ 2 → lỗi "name already in use").
- Đang test mà prod chậm/lỗi với người dùng thật → **Ctrl+C**; còn kẹt: `ssh unbutu 'docker rm -f k6-loadtest'`.
- Tìm nguyên nhân khi FAIL: SigNoz dashboard **unbutu – Service RED + JVM** (p99 theo service, pool DB, GC) + **Container** (CPU/RAM).
