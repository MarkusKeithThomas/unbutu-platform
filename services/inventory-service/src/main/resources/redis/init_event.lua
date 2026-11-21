-- Dựng trạng thái 1 sự kiện từ sổ cái Postgres (lần đầu, hoặc Redis mất dữ liệu). Chỉ chạy nếu CHƯA ready
-- (2 instance cùng dựng → chỉ 1 bản thắng, không ghi đè số đang bị trừ dở).
-- KEYS: 1 avail 2 total 3 status 4 ver 5 ready   ARGV: lặp lại từng bộ 5 giá trị (ticketTypeId, available, total, status, versionMillis)
if redis.call('EXISTS', KEYS[5]) == 1 then
  return 0
end
for i = 1, #ARGV, 5 do
  redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
  redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 2])
  redis.call('HSET', KEYS[3], ARGV[i], ARGV[i + 3])
  redis.call('HSET', KEYS[4], ARGV[i], ARGV[i + 4])
end
redis.call('SET', KEYS[5], '1')
return 1
