# event-contracts

Schema event (Java record, không phụ thuộc framework) dùng chung giữa producer và consumer.
Mọi event đi trong `EventEnvelope { eventId, type, occurredAt, payload }`, serialize JSON; Kafka key = id aggregate gốc.
Trace: OTel Java agent tự truyền `traceparent` qua Kafka header (không cần field trong envelope).

| Topic | Producer | Type | Consumer |
|---|---|---|---|
| `catalog.event.v1` (key = id sự kiện) | catalog (Outbox) | `event.upserted`, `event.deleted`, `ticket-type.upserted`, `ticket-type.deleted` | inventory (tồn kho), booking (bảng giá) – ORD-1b/1c |

Thay đổi phải **tương thích ngược** (chỉ thêm field optional). Đổi không tương thích → topic mới `.v2`.
