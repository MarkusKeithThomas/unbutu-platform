# inventory-service

**Tồn kho ghế – phần khó nhất**

| | |
|---|---|
| Trách nhiệm | Sơ đồ ghế theo suất, **giữ ghế tạm (hold) có TTL** ~10 phút, xác nhận/nhả ghế. Chống double-booking khi nhiều người chọn cùng ghế (optimistic lock / unique constraint / Redis SET NX). |
| Dữ liệu | Postgres `inventory_db`: showtime_seats (status AVAILABLE/HELD/SOLD, version), seat_holds. Redis cho hold TTL. |
| Event (Redpanda) | Nghe: `catalog.event.v1` (`ticket-type.upserted|deleted`). |
| Gọi đồng bộ | – (không gọi catalog: dữ liệu đến qua event) |
| Package | `com.unbutu.ticket.inventory` |
| Port trong container | 8080 |

## Đã có (ORD-1b, 2026-11-21)
- **Redis** (AOF everysec, `noeviction`) giữ "còn lại" + vé đang giữ có hạn; key hash tag `inv:{eventId}:…` (chạy được Redis Cluster).
  Mọi thay đổi số vé = 1 Lua script (`src/main/resources/redis/*.lua`) → **oversell = 0** (đo: 100 request song song / 10 vé → đúng 10 thành công).
- **Postgres `inventory_db`** (`stock`): sổ cái bền – tổng, đã bán, trạng thái. Redis trống → dựng lại `còn lại = tổng − đã bán`.
- Consumer **`catalog.event.v1`** (group `inventory-service`): snapshot có version → replay/đến trễ an toàn, không cần bảng dedupe.
- API công khai `GET /api/inventory/events/{eventId}/availability` → `[{ticketTypeId, available, total, state: ON_SALE|LOW|SOLD_OUT|PAUSED}]`,
  `Cache-Control: public, max-age=1` (đường đọc nóng nhất lúc mở bán, chỉ chạm Redis).
- API nội bộ (gateway không route): `POST /internal/events/{eventId}/reservations` `{reservationId, items[{ticketTypeId, quantity}], ttlSeconds}`
  → `200 RESERVED {expiresAt}` · `409 SOLD_OUT|NOT_ON_SALE {ticketTypeId}`; idempotent theo `reservationId`.
  `DELETE /internal/events/{eventId}/reservations/{reservationId}` → 204 (idempotent). Hết hạn: `HoldSweeper` (1 s) tự trả vé.
- **Chốt vé** (PAY-1): `POST /internal/events/{eventId}/reservations/{id}/commit {items}` – hold còn → xoá hold; hold đã nhả (trả muộn) → lấy lại từ số còn nếu đủ; rồi sổ cái `UPDATE stock SET sold = sold + n WHERE sold + n <= total` + bảng `commits` (idempotent) trong 1 transaction. Sổ cái từ chối → trả lại Redis → `409 SOLD_OUT` (booking hoàn tiền).
- **Giới hạn đã biết**: Redis mất sạch dữ liệu → vé đang giữ mất (bán lại được). Chặn ở bước chốt vé (ORD-1c/PAY):
  `UPDATE stock SET sold = sold + n WHERE sold + n <= total_quantity` – sổ cái không bao giờ bán quá; không đủ → hoàn tiền.

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
