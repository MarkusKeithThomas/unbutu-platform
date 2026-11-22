package com.unbutu.ticket.booking.loadtest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cờ ON/OFF theo sự kiện cho load test mua vé: sự kiện đang bật → bỏ giới hạn vé/tài khoản và khoá (tài khoản, sự kiện).
 * Đường nóng (giữ vé) chỉ đọc bản sao trong bộ nhớ, làm mới mỗi 5 s từ DB (không thêm query mỗi request; nhiều instance
 * booking đều thấy cờ trong ≤ 5 s). Luôn có hạn – tối đa {@link #MAX_TTL}.
 */
@Slf4j
@Component
public class LimitBypass {

    public static final Duration MAX_TTL = Duration.ofHours(2);

    public record Entry(UUID eventId, Instant expiresAt, String enabledBy) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private volatile Map<UUID, Instant> active = Map.of();

    LimitBypass(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** true nếu sự kiện đang bật cờ load test (đường nóng – chỉ đọc bộ nhớ). */
    public boolean active(UUID eventId) {
        Instant exp = active.get(eventId);
        return exp != null && clock.instant().isBefore(exp);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 0)
    void refresh() {
        try {
            active = list().stream().collect(Collectors.toUnmodifiableMap(Entry::eventId, Entry::expiresAt));
        } catch (RuntimeException e) {
            log.warn("Không đọc được limit_bypass – giữ bản cũ: {}", e.getMessage());
        }
    }

    List<Entry> list() {
        return jdbc.sql("select event_id, expires_at, enabled_by from limit_bypass where expires_at > :now")
                .param("now", Timestamp.from(clock.instant()))
                .query((rs, i) -> new Entry(rs.getObject("event_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("enabled_by")))
                .list();
    }

    Entry enable(UUID eventId, Duration ttl, String by) {
        Duration d = ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
        Instant now = clock.instant();
        jdbc.sql("""
                        insert into limit_bypass (event_id, expires_at, enabled_by, created_at) values (:e, :exp, :by, :now)
                        on conflict (event_id) do update set expires_at = excluded.expires_at, enabled_by = excluded.enabled_by""")
                .param("e", eventId).param("exp", Timestamp.from(now.plus(d))).param("by", by)
                .param("now", Timestamp.from(now)).update();
        log.warn("BẬT bỏ giới hạn vé (load test) cho sự kiện {} tới {} – bởi {}", eventId, now.plus(d), by);
        refresh();
        return new Entry(eventId, now.plus(d), by);
    }

    void disable(UUID eventId, String by) {
        jdbc.sql("delete from limit_bypass where event_id = :e").param("e", eventId).update();
        log.warn("TẮT bỏ giới hạn vé (load test) cho sự kiện {} – bởi {}", eventId, by);
        refresh();
    }
}
