# Runbook – kafka-ui (xem Redpanda prod)

`https://unbutu.tail3c11f4.ts.net:9443` – chỉ máy trong tailnet. **Chỉ đọc** (ADR-0008). Local: `http://localhost:8091` (có quyền thao tác).

## Bật lần đầu (admin, sau khi bản deploy có `kafka-ui` đã lên)
```bash
ssh unbutu 'docker ps --format "{{.Names}} {{.Status}}" | grep kafka-ui; curl -fsS http://127.0.0.1:8091/actuator/health && echo'
ssh unbutu 'sudo tailscale serve --bg --https=9443 http://127.0.0.1:8091 && tailscale serve status'
```
`serve status` phải có `:9443 (tailnet only)`; `:443` vẫn `(Funnel on)` → gateway. Kiểm tra từ ngoài tailnet phải **thất bại**:
`curl --resolve unbutu.tail3c11f4.ts.net:9443:$(dig +short @1.1.1.1 unbutu.tail3c11f4.ts.net | head -1) https://unbutu.tail3c11f4.ts.net:9443`
Tắt: `ssh unbutu 'sudo tailscale serve --https=9443 off'`.

## Trace bug thường gặp
| Câu hỏi | Ở đâu trên UI |
|---|---|
| Đơn trả tiền rồi mà chưa CONFIRMED? | Consumers → `booking-service` → lag `payment.result.v1`; Topics → `payment.result.v1` → Messages, lọc Key = bookingId |
| Ticketing/notification có nhận đơn? | Topics → `booking.event.v1` → Messages (key = bookingId) · Consumers → lag của group đó |
| Giá / tồn kho không cập nhật? | Consumers → `inventory-service` / `booking-service` → lag `catalog.event.v1` |
| Message hỏng bị bỏ qua? | Topics `*.DLT` (khi có) · log consumer (SigNoz) |
| Consumer có ổn định? | Consumers → State `STABLE`, Members, Instance ID `booking-1-…` / `inventory-1-…` (static membership) |

Lag tăng liên tục + State `PREPARING_REBALANCE` → xem mục dưới.

## Sửa dữ liệu (KHÔNG làm từ UI) – `rpk` qua SSH, có chủ đích
```bash
ssh unbutu 'docker exec prod-redpanda-1 rpk group describe booking-service'
# reset offset: PHẢI dừng consumer trước (group không còn member), ghi lại offset cũ để quay lại được
ssh unbutu 'docker exec prod-redpanda-1 rpk group seek <group> --to <offset|start|end> --topics <topic>'
```

## Group kẹt `PREPARING_REBALANCE` sau rollback (static membership)
Rollback `deploy.sh` từ bản có static membership (booking/inventory, `group.instance.id`) về bản cũ không có: member static
cũ **không gửi LeaveGroup** khi tắt → broker chờ nó tới hết `session.timeout.ms` (**60 s**), trong lúc đó consumer mới
không nhận partition (đo local 2026-11-21: app lên 09:28:01 → nhận partition 09:28:48, ~46 s). Tự hết, không cần làm gì;
đơn chậm xác nhận ≤ 1 phút. Deploy lên (dynamic → static) **không** bị: member dynamic gửi LeaveGroup khi tắt.
