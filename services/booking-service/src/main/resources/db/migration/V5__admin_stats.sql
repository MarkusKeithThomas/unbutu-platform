-- ADM-1: dashboard admin.
-- Cờ ẩn của sự kiện (catalog.event.v1 event.upserted.hidden) → thống kê mặc định loại sự kiện load test.
ALTER TABLE event_info ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT false;
-- Sự kiện ẩn tạo trước khi event có trường hidden: loadtest-service luôn đặt venue = 'Load test' (PurchaseFixture).
UPDATE event_info SET hidden = true WHERE venue = 'Load test';

-- Thống kê theo khoảng ngày (toàn bộ / theo sự kiện). Bảng còn nhỏ → CREATE INDEX thường (khoá ghi rất ngắn).
CREATE INDEX ix_bookings_created ON bookings (created_at);
CREATE INDEX ix_bookings_event_created ON bookings (event_id, created_at);
