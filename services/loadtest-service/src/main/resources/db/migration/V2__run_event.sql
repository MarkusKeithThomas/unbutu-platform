-- Kịch bản mua vé: sự kiện ẩn được tạo cho lượt này (đơn/vé test lọc theo event_id ở booking/notification).
ALTER TABLE runs ADD COLUMN event_id UUID;
