# catalog-service

**Danh mục sự kiện / phim**

| | |
|---|---|
| Trách nhiệm | Sự kiện (event/movie), địa điểm (venue), phòng/khu (hall), suất diễn (showtime), loại vé & giá cơ bản. Đọc nhiều → cache. |
| Dữ liệu | Postgres `catalog_db`: events, venues, halls, seat_layouts, showtimes, ticket_types. |
| Event (RabbitMQ) | Phát: `showtime.published`. |
| Gọi đồng bộ | – |
| Package | `com.unbutu.ticket.catalog` |
| Port trong container | 8080 |

Xem `ARCHITECTURE.md` §4 để biết quy ước chung.
