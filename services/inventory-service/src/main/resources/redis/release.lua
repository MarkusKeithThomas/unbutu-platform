-- Nhả vé đang giữ (huỷ / hết hạn) – idempotent: gọi lại khi hold đã nhả → không cộng lần 2.
-- KEYS: 1 avail 2 hold 3 holds   ARGV: 1 reservationId
local items = redis.call('HGETALL', KEYS[2])
redis.call('ZREM', KEYS[3], ARGV[1])
if #items == 0 then
  return 0
end
for i = 1, #items, 2 do
  -- Hạng vé đã bị xoá khỏi catalog trong lúc giữ → không tạo lại field.
  if redis.call('HEXISTS', KEYS[1], items[i]) == 1 then
    redis.call('HINCRBY', KEYS[1], items[i], tonumber(items[i + 1]))
  end
end
redis.call('DEL', KEYS[2])
return 1
