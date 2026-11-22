-- Sự kiện ẩn (vd. sự kiện load test): không hiện trong danh sách của khách, vẫn mở được / mua được qua id.
ALTER TABLE events ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
