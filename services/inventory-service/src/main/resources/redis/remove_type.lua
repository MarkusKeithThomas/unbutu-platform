-- Hạng vé bị xoá ở catalog (chỉ xoá được khi chưa từng mở bán).  KEYS: 1 avail 2 total 3 status 4 ver   ARGV: 1 tt
for i = 1, 4 do
  redis.call('HDEL', KEYS[i], ARGV[1])
end
return 1
