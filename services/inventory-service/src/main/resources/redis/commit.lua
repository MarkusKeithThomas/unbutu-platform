-- Chốt vé của đơn đã thanh toán (phần Redis). Sổ cái Postgres chốt sau; lỗi sổ cái → Java bù lại (compensate.lua).
-- Hold còn → xoá hold (vé KHÔNG quay lại kho). Hold mất (trả muộn / Redis mất dữ liệu) → lấy lại từ số còn nếu đủ.
-- KEYS: 1 avail 2 hold 3 holds   ARGV: 1 reservationId, rồi từng cặp ticketTypeId, qty
redis.call('ZREM', KEYS[3], ARGV[1])
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('DEL', KEYS[2])
  return {'FROM_HOLD'}
end
for i = 2, #ARGV, 2 do
  local avail = tonumber(redis.call('HGET', KEYS[1], ARGV[i]) or '0')
  if avail < tonumber(ARGV[i + 1]) then
    return {'SOLD_OUT', ARGV[i]}
  end
end
for i = 2, #ARGV, 2 do
  redis.call('HINCRBY', KEYS[1], ARGV[i], -tonumber(ARGV[i + 1]))
end
return {'LATE'}
