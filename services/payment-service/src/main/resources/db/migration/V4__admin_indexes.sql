-- ADM-3: tra cứu thanh toán cho admin – keyset (created_at, id), lọc theo trạng thái, tìm theo mã đơn.
CREATE INDEX ix_payments_created ON payments (created_at DESC, id DESC);
CREATE INDEX ix_payments_status_created ON payments (status, created_at DESC, id DESC);
CREATE INDEX ix_payments_booking_code ON payments (booking_code);
