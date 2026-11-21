-- Sổ cái từ chối chốt (đã bán đủ) sau khi Redis đã trừ → trả lại số đã trừ.  KEYS: 1 avail   ARGV: cặp ticketTypeId, qty
for i = 1, #ARGV, 2 do
  if redis.call('HEXISTS', KEYS[1], ARGV[i]) == 1 then
    redis.call('HINCRBY', KEYS[1], ARGV[i], tonumber(ARGV[i + 1]))
  end
end
return 1
