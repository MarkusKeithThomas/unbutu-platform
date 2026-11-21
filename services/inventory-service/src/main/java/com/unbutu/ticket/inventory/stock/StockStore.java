package com.unbutu.ticket.inventory.stock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Tồn kho trên Redis – mọi thao tác thay đổi số vé là 1 Lua script (atomic). Sổ cái Postgres ({@link StockLedger})
 * dùng để dựng lại khi Redis trống.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockStore {

    public enum ReserveOutcome { RESERVED, SOLD_OUT, NOT_ON_SALE }

    public record ReserveResult(ReserveOutcome outcome, UUID ticketTypeId, Instant expiresAt) {
    }

    /** Tồn kho 1 hạng vé tại thời điểm đọc. */
    public record TypeStock(UUID ticketTypeId, int available, int total, String status) {
    }

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RESERVE = script("reserve", List.class);
    private static final RedisScript<Long> RELEASE = script("release", Long.class);
    private static final RedisScript<Long> INIT_EVENT = script("init_event", Long.class);
    private static final RedisScript<String> UPSERT_TYPE = script("upsert_type", String.class);
    private static final RedisScript<Long> REMOVE_TYPE = script("remove_type", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> READ_EVENT = script("read_event", List.class);

    private final StringRedisTemplate redis;
    private final StockLedger ledger;

    private static <T> RedisScript<T> script(String name, Class<T> type) {
        return RedisScript.of(new ClassPathResource("redis/" + name + ".lua"), type);
    }

    // ---------- giữ / nhả ----------

    /** items: ticketTypeId → số lượng (đã gộp trùng, > 0). Idempotent theo reservationId. */
    public ReserveResult reserve(UUID eventId, UUID reservationId, Map<UUID, Integer> items, Instant expiresAt, long ttlSeconds) {
        var keys = new StockKeys(eventId);
        List<String> args = new ArrayList<>();
        args.add(reservationId.toString());
        args.add(Long.toString(expiresAt.toEpochMilli()));
        args.add(Long.toString(ttlSeconds));
        items.forEach((tt, qty) -> {
            args.add(tt.toString());
            args.add(Integer.toString(qty));
        });
        List<String> k = List.of(keys.avail(), keys.status(), keys.hold(reservationId), keys.holds(), keys.ready());
        List<?> r = redis.execute(RESERVE, k, args.toArray());
        if ("NOT_READY".equals(r.getFirst())) {
            ensureReady(eventId);
            r = redis.execute(RESERVE, k, args.toArray());
        }
        String outcome = (String) r.getFirst();
        return switch (outcome) {
            case "RESERVED" -> {
                redis.opsForSet().add(StockKeys.EVENTS_WITH_HOLDS, eventId.toString());
                yield new ReserveResult(ReserveOutcome.RESERVED, null,
                        Instant.ofEpochMilli((long) Double.parseDouble(String.valueOf(r.get(1)))));
            }
            case "SOLD_OUT" -> new ReserveResult(ReserveOutcome.SOLD_OUT, UUID.fromString((String) r.get(1)), null);
            case "NOT_ON_SALE" -> new ReserveResult(ReserveOutcome.NOT_ON_SALE, UUID.fromString((String) r.get(1)), null);
            default -> throw new IllegalStateException("reserve.lua trả kết quả lạ: " + r);
        };
    }

    /** true nếu thật sự trả vé (false: đã nhả trước đó / không có). */
    public boolean release(UUID eventId, UUID reservationId) {
        var keys = new StockKeys(eventId);
        Long n = redis.execute(RELEASE, List.of(keys.avail(), keys.hold(reservationId), keys.holds()), reservationId.toString());
        return n != null && n == 1;
    }

    /** reservationId đã hết hạn của 1 sự kiện (tối đa limit). */
    public List<UUID> expiredHolds(UUID eventId, Instant now, int limit) {
        var ids = redis.opsForZSet().rangeByScore(new StockKeys(eventId).holds(), 0, now.toEpochMilli(), 0, limit);
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    public boolean hasHolds(UUID eventId) {
        Long n = redis.opsForZSet().zCard(new StockKeys(eventId).holds());
        return n != null && n > 0;
    }

    // ---------- đồng bộ từ catalog ----------

    public String upsertType(UUID eventId, UUID ticketTypeId, int total, String status, long versionMillis, int sold) {
        return redis.execute(UPSERT_TYPE, new StockKeys(eventId).typeState(),
                ticketTypeId.toString(), Integer.toString(total), status, Long.toString(versionMillis), Integer.toString(sold));
    }

    public void removeType(UUID eventId, UUID ticketTypeId) {
        var keys = new StockKeys(eventId);
        redis.execute(REMOVE_TYPE, List.of(keys.avail(), keys.total(), keys.status(), keys.ver()), ticketTypeId.toString());
    }

    /**
     * Dựng 1 sự kiện từ sổ cái nếu Redis chưa có. Sau khi dựng, áp lại từng dòng bằng upsert_type (có version)
     * để vá trường hợp consumer cập nhật Postgres đúng lúc đang dựng (bản dựng có thể đọc snapshot cũ hơn).
     */
    public void ensureReady(UUID eventId) {
        var keys = new StockKeys(eventId);
        if (Boolean.TRUE.equals(redis.hasKey(keys.ready()))) {
            return;
        }
        List<StockLedger.Row> rows = ledger.forEvent(eventId);
        List<String> args = new ArrayList<>();
        for (var r : rows) {
            args.add(r.ticketTypeId().toString());
            args.add(Integer.toString(r.available()));
            args.add(Integer.toString(r.totalQuantity()));
            args.add(r.status());
            args.add(Long.toString(r.versionMillis()));
        }
        Long built = redis.execute(INIT_EVENT, List.of(keys.avail(), keys.total(), keys.status(), keys.ver(), keys.ready()), args.toArray());
        if (built != null && built == 1) {
            log.info("Tồn kho: dựng sự kiện {} từ sổ cái ({} hạng vé)", eventId, rows.size());
            for (var r : ledger.forEvent(eventId)) {
                upsertType(eventId, r.ticketTypeId(), r.totalQuantity(), r.status(), r.versionMillis(), r.sold());
            }
        }
    }

    // ---------- đọc ----------

    /** 1 round-trip Redis. Rỗng nếu sự kiện chưa có hạng vé nào. */
    public List<TypeStock> read(UUID eventId) {
        var keys = new StockKeys(eventId);
        List<String> k = List.of(keys.avail(), keys.total(), keys.status(), keys.ready());
        List<?> r = redis.execute(READ_EVENT, k);
        if ("NOT_READY".equals(r.getFirst())) {
            ensureReady(eventId);
            r = redis.execute(READ_EVENT, k);
        }
        List<TypeStock> out = new ArrayList<>((r.size() - 1) / 4);
        for (int i = 1; i + 3 < r.size(); i += 4) {   // r[0] = 'OK', rồi từng bộ (tt, available, total, status)
            out.add(new TypeStock(UUID.fromString((String) r.get(i)), Math.max(0, Integer.parseInt((String) r.get(i + 1))),
                    Integer.parseInt((String) r.get(i + 2)), (String) r.get(i + 3)));
        }
        return out;
    }
}
