-- ADM-2: danh sách đơn cho admin – lọc theo trạng thái (keyset created_at, id) + tìm chính xác theo email / SĐT người mua.
CREATE INDEX ix_bookings_status_created ON bookings (status, created_at DESC, id DESC);
CREATE INDEX ix_bookings_buyer_email ON bookings (lower(buyer_email)) WHERE buyer_email IS NOT NULL;
CREATE INDEX ix_bookings_buyer_phone ON bookings (buyer_phone) WHERE buyer_phone IS NOT NULL;
