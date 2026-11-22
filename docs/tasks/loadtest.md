# Task – load test (S6)

Mục tiêu: chạy load test theo **bậc rps** (vd. 50 → 100 → 200 → 400), xem trực tiếp, biết hệ thống **"PASS tới X rps"**
(p95 < 300 ms, lỗi < 1%, bỏ lượt < 5%). Gắn với ARCHITECTURE §8 S6. Cách chạy: `docs/runbooks/loadtest.md`.

**Đã chốt 2026-11-22:** bản 1 = **k6 + web dashboard có sẵn của k6** (không service mới): chạy bằng `make loadtest STEPS=…`,
k6 chạy trên unbutu (`--cpus 1`) gọi thẳng api-gateway trong mạng Docker, xem dashboard qua `ssh -L` (localhost:5665).
Nút bấm trên web (`loadtest-service`) để sau, dùng lại kịch bản k6.

## Đang mở
- [ ] **LT-A · Chạy thật trên prod, tìm điểm gãy** của `browse` (người dùng): vd. `make loadtest STEPS=100,200,400,800`
  rồi dò kỹ quanh bậc FAIL. Ghi `docs/loadtest/baseline.md`: X rps, p95/p99, nghẽn ở đâu (SigNoz RED + Container).
  Ghi nhận: service đang chạy **Serial GC** vì `mem_limit 512m` (< 1792 MB → JVM chọn Serial), heap tối đa 384 MB – ứng viên nghẽn.
  Lần chạy thử 5/10 rps: p99 bậc 10 rps = 847 ms (JIT còn nguội?) – xem lại khi chạy thật.
- [ ] **LT-B · Kịch bản `hold` / `checkout`**: tài khoản test (giới hạn 4 vé/tài khoản/sự kiện → nhiều tài khoản hoặc ngoại lệ),
  sự kiện "Load Test" reset được, dọn dữ liệu sau mỗi lượt, cổng thanh toán giả lập chỉ cho ADMIN/test (thay PAY-1c (3)).
- [ ] **LT-C · Nút bấm trên web – rollout prod**: code xong (PR feat/loadtest-ui-service): `loadtest-service` + trang `/admin/loadtest`
  (chạy/dừng, biểu đồ rps + p95 trực tiếp, bảng PASS/FAIL, "Chịu được X rps", lịch sử). Trước khi Approve deploy: secret
  `unbutu/prod/loadtest-service#DB_PASSWORD` + PR pro-ticket-workload (env.yaml + `SERVICE_DATABASES`) + tạo `loadtest_db` (runbook add-service-database).
- [ ] **LT-E · k6 → SigNoz + dashboard "Load test"** (sau LT-C): k6 `--out opentelemetry` vào collector, 1 dashboard ghép rps/p95 của k6
  với CPU container + p99 từng service để thấy nghẽn ở đâu cùng một trục thời gian.
- [ ] **LT-D · Alert rule trên UI SigNoz** (OBS-5, hoãn): 3 rule theo runbook `signoz.md` §Alert + diễn tập.

## Đã xong
- 2026-11-22: LT-1 phần dashboard – `deploy/signoz/dashboards/service-red.json` (#43), đã import, hiện đủ panel.
- 2026-11-22: Bản 1 load test – `make loadtest` (k6 theo bậc, dashboard trực tiếp, bảng PASS/FAIL từng bậc, báo cáo HTML/JSON).
  Chạy thử prod 5 → 10 rps (10 s/bậc): PASS.
