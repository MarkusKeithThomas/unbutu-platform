# Task – load test (S6)

Mục tiêu: người dùng đăng nhập bằng tài khoản role `LOADTEST`, chọn kịch bản có sẵn, bấm chạy, xem kết quả trực tiếp
(phía client + phía server) và kiểm tra đúng đắn sau test (oversell = 0). Gắn với ARCHITECTURE §8 S6.

**Đã chốt 2026-11-22:**
1. k6 chạy **trên unbutu**, trong `loadtest-service`, có giới hạn CPU. Kết quả ghi rõ là đo nội bộ (gateway trong mạng Docker, không đi qua Funnel).
2. Quyền chạy: **tài khoản role `LOADTEST`** (ADMIN cấp cho từng người), có hạn mức lượt/ngày + audit log.
3. Thứ tự **A → B → C**: chạy tay trước để biết sức chịu của máy rồi mới làm UI.

```
Người dùng ─────► frontend /loadtest ─► api-gateway ─► loadtest-service
                                                         ├─ khoá Redis: 1 lượt chạy tại 1 thời điểm
                                                         ├─ k6 (tiến trình con, cpus giới hạn) ─► api-gateway ─► các service
                                                         └─ số liệu theo giây ─► Postgres loadtest_db ─► SSE / lịch sử
```

## Đang mở

### Giai đoạn A – Chuẩn bị + chạy tay (S6)
- [ ] **LT-1 · OBS-5 trước**: dashboard RED theo service + JVM (`docs/tasks/observability.md`). Thiếu thì không biết nghẽn ở đâu.
- [ ] **LT-2 · Giới hạn tài nguyên container prod** (`cpus` / `mem_limit` trong `deploy/compose.app.yml`), chừa phần cho
  Postgres, Redis, Redpanda, SigNoz. Load test không được làm sập hạ tầng.
- [ ] **LT-3 · Dữ liệu test**: sự kiện "Load Test" (tồn kho lớn, có thể reset), lô tài khoản test role `LOADTEST`,
  job dọn sau mỗi lượt (nhả giữ chỗ, xoá đơn/vé test, đặt lại tồn kho). Dữ liệu test không lẫn vào số liệu thật.
  Cân nhắc giới hạn 4 vé/tài khoản/sự kiện → cần nhiều tài khoản test hoặc ngoại lệ cho sự kiện test.
- [ ] **LT-4 · Chặn cổng giả lập (thay PAY-1c (3))**: chỉ ADMIN + `LOADTEST` được "trả tiền" giả lập trên prod.
- [ ] **LT-5 · Kịch bản k6** trong `loadtest/k6/`:
  - `browse`: danh sách sự kiện → chi tiết → "Còn x/y" (đường cache, rps cao).
  - `flash-sale`: nhiều người cùng giữ vé 1 hạng → chứng minh Redis Lua, oversell = 0.
  - `checkout`: giữ vé → thanh toán giả lập → vé QR (đi hết Kafka/outbox).
- [ ] **LT-6 · Chạy tay, tìm điểm gãy**: số nền rps / p95 / p99 từng kịch bản, nguyên nhân nghẽn (pool, GC, CPU) bằng SigNoz
  + JFR; ghi `docs/loadtest/baseline.md`. Từ đó đặt **trần** cho UI (VU, rps, thời lượng). ADR + tick S6.

### Giai đoạn B – UI chạy test
- [ ] **LT-7 · `loadtest-service`** (Spring Boot, image có binary k6, `libs:platform-starter`), theo checklist service mới trong CLAUDE.md
  (env.yaml ở pro-ticket-workload, `loadtest_db`, compose app/local, port local):
  `POST /api/loadtest/runs` (chỉ preset + mức tải trong trần, **không nhận script tự do**), `GET /api/loadtest/runs/{id}`,
  `GET /api/loadtest/runs/{id}/stream` (SSE), `DELETE /api/loadtest/runs/{id}` (dừng), `GET /api/loadtest/runs` (lịch sử).
  Số liệu: đọc REST API của k6 (`--address`) mỗi giây → lưu `run_metrics`.
- [ ] **LT-8 · Rào chắn**: khoá Redis 1 lượt/lúc (người khác thấy "đang chạy" + xem chung), thời lượng tối đa, hạn mức lượt/ngày/tài khoản,
  tự dừng khi lỗi > X% hoặc p99 vượt ngưỡng, nút dừng khẩn cấp cho ADMIN, audit log (ai, kịch bản, mức tải).
- [ ] **LT-9 · Role `LOADTEST`** ở identity-service + màn ADMIN cấp/thu hồi tài khoản (hoặc mã mời có hạn).
- [ ] **LT-10 · Trang `/loadtest`** (frontend): chọn kịch bản (mô tả đang chứng minh điều gì), thanh trượt mức tải trong trần,
  Chạy / Dừng, trang giới thiệu kiến trúc ngắn.

### Giai đoạn C – UI xem kết quả
- [ ] **LT-11 · Màn trực tiếp**: rps, p50/p95/p99, tỉ lệ lỗi, VU theo thời gian (SSE, 1 giây/điểm).
- [ ] **LT-12 · Phía server**: CPU/RAM từng container, lag Kafka, độ trễ theo service – loadtest-service truy vấn API SigNoz
  (người dùng ngoài không vào được tailnet). Key API SigNoz để trên AWS (ADR-0004).
- [ ] **LT-13 · Kiểm tra đúng đắn sau test**: đã bán ≤ tổng (oversell = 0), tiền thu khớp số vé phát, outbox không còn tồn, vé phát = đơn CONFIRMED.
- [ ] **LT-14 · Lịch sử + so sánh** 2 lượt cạnh nhau, link chia sẻ kết quả (chỉ đọc).

### Giai đoạn D – Hoàn thiện
- [ ] **LT-15** · ADR thiết kế load test, runbook "load test làm prod chậm/sập" (`docs/runbooks/`).

## Đã xong
