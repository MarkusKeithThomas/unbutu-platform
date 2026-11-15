# inventory-service

**Tồn kho ghế – phần khó nhất**

| | |
|---|---|
| Trách nhiệm | Sơ đồ ghế theo suất, **giữ ghế tạm (hold) có TTL** ~10 phút, xác nhận/nhả ghế. Chống double-booking khi nhiều người chọn cùng ghế (optimistic lock / unique constraint / Redis SET NX). |
| Dữ liệu | Postgres `inventory_db`: showtime_seats (status AVAILABLE/HELD/SOLD, version), seat_holds. Redis cho hold TTL. |
| Event (RabbitMQ) | Nghe: `showtime.published` (sinh ghế). Phát: `seats.released`. |
| Gọi đồng bộ | Gọi `catalog-service` lấy layout. |
| Package | `com.unbutu.ticket.inventory` |
| Port trong container | 8080 |

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
