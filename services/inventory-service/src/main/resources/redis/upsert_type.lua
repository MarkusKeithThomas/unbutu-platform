-- Áp snapshot hạng vé từ catalog. Idempotent + chống bản cũ: chỉ áp khi version mới hơn bản đang có.
-- Đổi tổng số vé: còn lại += (tổng mới - tổng cũ) → không đụng tới vé đang giữ.
-- Sự kiện chưa ready → bỏ qua (lần dựng sau đọc thẳng Postgres, đã cập nhật trước bước này).
-- KEYS: 1 avail 2 total 3 status 4 ver 5 ready   ARGV: 1 tt 2 newTotal 3 status 4 versionMillis 5 sold
if redis.call('EXISTS', KEYS[5]) == 0 then
  return 'SKIP_NOT_READY'
end
local cur = redis.call('HGET', KEYS[4], ARGV[1])
if cur and tonumber(cur) >= tonumber(ARGV[4]) then
  return 'SKIP_STALE'
end
local oldTotal = redis.call('HGET', KEYS[2], ARGV[1])
if oldTotal then
  redis.call('HINCRBY', KEYS[1], ARGV[1], tonumber(ARGV[2]) - tonumber(oldTotal))
else
  redis.call('HSET', KEYS[1], ARGV[1], tonumber(ARGV[2]) - tonumber(ARGV[5]))
end
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('HSET', KEYS[3], ARGV[1], ARGV[3])
redis.call('HSET', KEYS[4], ARGV[1], ARGV[4])
return 'APPLIED'
