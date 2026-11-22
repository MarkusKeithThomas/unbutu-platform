# Postmortem – deploy `ca1c08e` fail, rollback cũng báo fail (2026-11-22)

## Tóm tắt
Deploy bản `ca1c08e` (#47 – load test mua vé) lên prod fail vì `catalog-service` không healthy kịp. `deploy.sh` rollback
về `fb0efce` nhưng rollback **cũng** báo fail với cùng lý do → "cần xử lý tay". Thực tế vài giây sau mọi container đều
healthy ở bản `fb0efce`; không cần thao tác tay. Không mất dữ liệu.

## Mốc thời gian (UTC)
| Giờ | Sự kiện |
|---|---|
| 04:40:32 | Deploy `ca1c08e` bắt đầu (sinh env từ AWS – có `loadtest-service.env` đủ biến) |
| 04:41:00 | `compose up --wait`: khởi động lại cùng lúc 9 service Java |
| 04:43:45 | Log đầu tiên của catalog bản mới (`Starting CatalogServiceApplication`) – JVM đã chạy ~2,5′ mới tới đây |
| 04:44:30 | Flyway V5 (`events.hidden`) xong trong 0,3 s; booking V4 xong 04:44:37 |
| 04:45:06 | catalog `unhealthy` (hết 30 s start_period + 12 × 10 s) → rollback về `fb0efce` |
| 04:47:53 | Rollback: catalog lại `unhealthy` → deploy kết thúc lỗi |
| ~04:48 | Catalog `fb0efce` healthy; API công khai 200 |

## Ảnh hưởng
- Khoảng 04:41–04:48: các service khởi động lại 2 lần → API có thể chậm / 502 từng lúc (không có người dùng thật đang dùng).
- Prod ở lại `fb0efce` (có loadtest-service, chưa có kịch bản mua vé). DB đã ở schema mới (catalog V5, booking V4) –
  tương thích ngược: code cũ không đọc cột/bảng mới.

## Nguyên nhân gốc
**Thiếu CPU lúc khởi động đồng loạt**, không phải lỗi code (migration nhanh, log không có exception, local + CI smoke đạt):
- Máy 4 core; deploy khởi động lại cùng lúc 9 JVM (thêm `loadtest-service` từ #45) + OpenTelemetry Java agent
  (agent làm khởi động chậm đáng kể). Từ "Starting" tới Flyway mất 45 s – bình thường ~5 s.
- Healthcheck chung (`x-service`) chỉ cho 30 s `start_period` + 12 lần × 10 s = **150 s**, trong khi `deploy.sh` chờ tới 300 s.
  Catalog (JPA/Hibernate – khởi động nặng nhất) vượt 150 s → bị đánh dấu `unhealthy` dù đang khởi động bình thường.
- Rollback cũng khởi động lại tất cả cùng lúc → lặp lại đúng điều kiện đó.

## Khắc phục
- `deploy/compose.app.yml`: `start_period` 30 s → **180 s** cho service Java (180 + 12 × 10 = 300 s = `--wait-timeout`).
  Service healthy sớm thì deploy đi tiếp ngay – chỉ nới trần, không làm chậm deploy bình thường.

## Bài học / việc sau
- Thêm service = thêm tải lúc deploy trên cùng 4 core – xem lại ngân sách CPU khởi động (ARCHITECTURE §4.6) mỗi khi thêm JVM.
- Có thể: chỉ khởi động lại service có image đổi (compose đã làm vậy theo digest – lần này hầu hết image đều đổi vì
  cùng commit), giới hạn `cpus` từng service, hoặc CDS/AppCDS để JVM khởi động nhanh hơn.
- Thông báo "rollback cũng lỗi – cần xử lý tay" nên kèm trạng thái thật sau vài phút (container có thể healthy muộn).
