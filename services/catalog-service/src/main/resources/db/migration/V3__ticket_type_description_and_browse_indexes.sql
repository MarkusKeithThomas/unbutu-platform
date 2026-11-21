-- Mô tả ngắn của hạng vé (màn chọn vé: "Khu ngồi sát sân khấu, quà tặng").
ALTER TABLE ticket_types ADD COLUMN description VARCHAR(255);

-- Đường đọc nóng của khách (danh sách sự kiện đang bán + hạng vé của 1 sự kiện):
-- lọc theo (event_id, status) và lấy min(price) chỉ từ index (index-only scan khi bảng lớn).
DROP INDEX ix_ticket_types_event;
CREATE INDEX ix_ticket_types_event_status_price ON ticket_types (event_id, status, price);

-- Sắp xếp/lọc sự kiện sắp diễn ra.
CREATE INDEX ix_events_starts_at ON events (starts_at);
