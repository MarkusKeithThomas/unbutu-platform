-- PAY-1: trạng thái thanh toán. PENDING → AWAITING_PAYMENT → CONFIRMED; trả muộn mà hết vé → REFUND_PENDING → REFUNDED.
ALTER TABLE bookings
    ADD COLUMN payment_method VARCHAR(16),
    ADD COLUMN confirmed_at   TIMESTAMPTZ;

-- Job hết hạn quét cả đơn đang chờ cổng thanh toán (có 5′ ân hạn).
DROP INDEX ix_bookings_pending_expiry;
CREATE INDEX ix_bookings_open_expiry ON bookings (expires_at) WHERE status IN ('PENDING', 'AWAITING_PAYMENT');
