-- Đọc tồn kho 1 sự kiện trong 1 round-trip (đường đọc nóng A8b).  KEYS: 1 avail 2 total 3 status 4 ready
-- Trả mảng PHẲNG (client Spring không giải mã được mảng lồng): {'OK', tt, available, total, status, tt, ...}
if redis.call('EXISTS', KEYS[4]) == 0 then
  return {'NOT_READY'}
end
local out = {'OK'}
local totals = redis.call('HGETALL', KEYS[2])
for i = 1, #totals, 2 do
  local tt = totals[i]
  out[#out + 1] = tt
  out[#out + 1] = redis.call('HGET', KEYS[1], tt) or '0'
  out[#out + 1] = totals[i + 1]
  out[#out + 1] = redis.call('HGET', KEYS[3], tt) or 'DRAFT'
end
return out
