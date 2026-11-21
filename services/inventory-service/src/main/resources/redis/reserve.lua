-- Giữ vé cho 1 đơn – ATOMIC (Redis chạy Lua đơn luồng ⇒ 2 đơn không thể cùng lấy 1 vé cuối ⇒ oversell = 0).
-- Mọi key cùng hash tag {eventId} → cùng slot, chạy được trên Redis Cluster.
-- KEYS: 1 avail(HASH tt→còn lại) 2 status(HASH tt→trạng thái) 3 hold(HASH tt→qty của đơn) 4 holds(ZSET đơn→hạn) 5 ready
-- ARGV: 1 reservationId 2 expiresAtMillis 3 holdTtlSeconds 4.. cặp ticketTypeId, qty
if redis.call('EXISTS', KEYS[5]) == 0 then
  return {'NOT_READY'}                                   -- chưa dựng từ sổ cái → Java dựng rồi gọi lại
end
if redis.call('EXISTS', KEYS[3]) == 1 then              -- gọi lại cùng reservationId (retry) → trả kết quả cũ
  return {'RESERVED', redis.call('ZSCORE', KEYS[4], ARGV[1])}
end
-- Kiểm tra TẤT CẢ trước: thiếu 1 hạng → không trừ gì.
for i = 4, #ARGV, 2 do
  local tt, qty = ARGV[i], tonumber(ARGV[i + 1])
  if redis.call('HGET', KEYS[2], tt) ~= 'ON_SALE' then
    return {'NOT_ON_SALE', tt}
  end
  local avail = tonumber(redis.call('HGET', KEYS[1], tt) or '0')
  if avail < qty then
    return {'SOLD_OUT', tt}
  end
end
for i = 4, #ARGV, 2 do
  redis.call('HINCRBY', KEYS[1], ARGV[i], -tonumber(ARGV[i + 1]))
  redis.call('HSET', KEYS[3], ARGV[i], ARGV[i + 1])
end
-- Hold sống thêm 2′ sau hạn: sweeper (ZSET) mới là nơi trả vé; TTL chỉ dọn rác nếu sweeper chết.
redis.call('EXPIRE', KEYS[3], tonumber(ARGV[3]) + 120)
redis.call('ZADD', KEYS[4], ARGV[2], ARGV[1])
return {'RESERVED', ARGV[2]}
