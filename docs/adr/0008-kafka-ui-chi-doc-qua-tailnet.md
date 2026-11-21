# ADR-0008: kafka-ui (chỉ đọc) xem Redpanda prod qua tailnet

- Trạng thái: Chấp nhận · 2026-11-21
- Liên quan: ADR-0006 (mẫu mở UI chỉ trong tailnet), ADR-0007 (Redpanda + Outbox)

## Bối cảnh
Trace bug luồng bất đồng bộ (catalog → inventory/booking, payment → booking, booking → ticketing/notification) cần xem:
consumer group có lag không, message cụ thể của 1 đơn (key = bookingId), topic DLT có gì. Trước đây chỉ có
`ssh unbutu 'docker exec prod-redpanda-1 rpk …'` – dùng được nhưng chậm, không lướt message được.

Ở công ty người dùng: một web UI Kafka sau domain nội bộ (VPN/SSO). UI chạy cạnh broker, trình duyệt chỉ nói HTTP với UI
→ broker không phải mở port hay quảng bá địa chỉ ra ngoài.

Phương án đã loại: mở listener Kafka thứ 2 + SSH tunnel cho client trên Mac – phải sửa/restart Redpanda, và một
service local trỏ nhầm vào prod sẽ vào cùng consumer group (cùng `group.instance.id`) → **fence consumer prod**.

## Quyết định
1. **kafbat/kafka-ui** (`ghcr.io/kafbat/kafka-ui`, pin phiên bản) trong project `prod` (`deploy/compose.app.yml`), deploy
   qua CI/`deploy.sh` như mọi container khác; nói với broker qua `redpanda:9092` nội bộ. Redpanda không đổi gì.
   Chọn kafbat thay Redpanda Console vì có cờ **`KAFKA_CLUSTERS_0_READONLY`** (bản Community của Console không có chế độ chỉ đọc).
2. **Prod chỉ đọc**: xem topic/message/consumer group/lag/DLT; xoá topic, gửi message, reset offset → 405 (đã kiểm).
   `DYNAMIC_CONFIG_ENABLED=false` (không sửa cấu hình cluster từ UI). Sửa dữ liệu Kafka prod = việc vận hành có chủ đích,
   làm bằng `rpk` qua SSH theo runbook. Local (`compose.local.yml`): cho phép thao tác để luyện.
3. **Chỉ trong tailnet**: publish `127.0.0.1:8091` → `tailscale serve --bg --https=9443 http://127.0.0.1:8091`
   → `https://unbutu.tail3c11f4.ts.net:9443`, **không Funnel**. Không bật đăng nhập (`AUTH_TYPE=DISABLED`) – lớp bảo vệ là tailnet.
   Đây là ngoại lệ thứ 2 của quy tắc "chỉ gateway publish port" (sau SigNoz `127.0.0.1:3301`).
4. `mem_limit: 512m` (đo ~370 MB).

## Hệ quả
- Dữ liệu message (email, SĐT người mua trong `booking.event.v1`) xem được bởi mọi máy trong tailnet → chỉ thêm máy tin cậy
  vào tailnet; khi có người ngoài cần xem: bật OAuth/RBAC của kafka-ui (`AUTH_TYPE=OAUTH2`) trước.
- kafka-ui unhealthy làm `deploy.sh` rollback cả bản deploy (như mọi container trong project `prod`).
- Đích AWS (MSK): cùng mô hình – UI trong VPC sau SSO, không mở broker.
