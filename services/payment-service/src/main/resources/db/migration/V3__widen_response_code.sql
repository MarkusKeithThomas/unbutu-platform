-- Mã kết quả nội bộ của đối soát (ABANDONED, NOT_PAID, REPLACED…) dài hơn mã 2 ký tự của VNPay.
ALTER TABLE payments ALTER COLUMN provider_response_code TYPE VARCHAR(16);
