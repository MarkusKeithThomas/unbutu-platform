-- Kịch bản mua vé: ngưỡng p95 "bấm mua → có QR" (ms) của lượt chạy. NULL = kịch bản không đo QR.
ALTER TABLE runs ADD COLUMN qr_p95_ms INT;
