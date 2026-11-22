-- Cờ load test: sự kiện có dòng ở đây (chưa hết hạn) → BỎ giới hạn 4 vé/tài khoản/sự kiện VÀ khoá (tài khoản, sự kiện),
-- để 1 tài khoản test mua song song nhiều luồng. Chỉ ADMIN bật (thường do loadtest-service bật cho sự kiện ẩn của nó);
-- luôn có hạn (expires_at) → service load test chết giữa chừng thì cờ tự tắt.
CREATE TABLE limit_bypass (
    event_id   UUID PRIMARY KEY,
    expires_at TIMESTAMPTZ  NOT NULL,
    enabled_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
