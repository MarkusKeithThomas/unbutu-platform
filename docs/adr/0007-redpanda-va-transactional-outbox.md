# ADR-0007: Redpanda (Kafka API) + Transactional Outbox cho event giữa service

- Trạng thái: Chấp nhận (2026-11-21). Thay quyết định "Message broker: RabbitMQ" trong ARCHITECTURE §12.
- Bối cảnh: ORD-1 (giữ vé) cần catalog → inventory (tổng số vé) và → booking (giá) **không gọi sync**,
  để catalog sập vẫn bán được vé; sau đó Saga thanh toán (booking ↔ payment ↔ inventory) cũng đi bằng event.

## Quyết định
1. **Redpanda** 1 node trên `unbutu`, dùng **Kafka API** (client `spring-kafka` chuẩn).
2. Producer ghi event vào **bảng `outbox` trong cùng transaction** với thay đổi nghiệp vụ; `OutboxRelay`
   (scheduler 500 ms, `pg_try_advisory_xact_lock` → 1 relay/lúc) gửi lên topic rồi đánh dấu `published_at`.
3. Giao **ít nhất 1 lần**: consumer phải idempotent theo `EventEnvelope.eventId` và bỏ snapshot cũ theo `updatedAt`.
4. Topic do producer khai báo (`NewTopic`), broker **tắt auto-create**. Key = id aggregate gốc (vd. id sự kiện) → thứ tự theo key.
5. Prod chạy **chế độ production** (tắt `developer_mode` của image: có fsync + syscheck, cần `--memory=1G`);
   local giữ developer mode 512 MB.

## Lý do
- Đích AWS trong thiết kế dùng Kafka/MSK → cùng API, chuyển lên MSK chỉ đổi `KAFKA_BOOTSTRAP_SERVERS`.
- Event log **replay được**: service mới (inventory) dựng trạng thái từ đầu topic; RabbitMQ xoá message sau khi ack.
- Consumer group + partition = scale ngang consumer theo số partition (đường tới 50k rps).
- Redpanda: 1 binary, không ZooKeeper/JVM; đo ~100 MB RAM khi tải nhẹ.
- Outbox (thay vì gửi Kafka trong request): không có "DB lưu xong mà event mất" hay ngược lại; broker tắt thì API vẫn chạy
  (đã thử: PUT/GET 200 khi Redpanda dừng, event gửi bù khi bật lại).

## Hệ quả / giới hạn
- 1 node, replication 1: Redpanda hỏng ổ là mất event chưa được consumer đọc → nguồn sự thật vẫn là Postgres của từng service;
  catalog có thể phát lại snapshot (migration V4 đã có mẫu backfill).
- Relay 1 instance/lúc ≈ vài nghìn event/s – đủ cho catalog. Service ghi nhiều (booking) khi cần: chia outbox theo key cho nhiều relay
  hoặc CDC (Debezium đọc WAL).
- Thứ tự theo `id` outbox ≈ thứ tự commit; 2 transaction cùng key commit đảo nhau hiếm khi xảy ra → consumer so `updatedAt`.
- Thêm ~1,3 GB `mem_limit` trên server (RAM còn ~8,6 GB khi đo).
