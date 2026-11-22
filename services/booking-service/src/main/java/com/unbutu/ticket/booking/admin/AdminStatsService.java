package com.unbutu.ticket.booking.admin;

import com.unbutu.ticket.booking.web.BookingException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Thống kê đơn hàng cho admin – đọc thẳng booking_db (ADM-1, phương án A).
 * <p>
 * 50k rps: đây là đường của vài admin, không phải khách – nhưng không được làm chậm đường mua vé. Kết quả cache
 * {@link #TTL} trong bộ nhớ theo (khoảng ngày, sự kiện, cờ test) → mỗi tổ hợp tối đa ~1 lượt quét/phút/instance;
 * truy vấn chạy trên index {@code (created_at)} / {@code (event_id, created_at)}; khoảng ngày ≤ {@link #MAX_DAYS}.
 * Khi bảng lên hàng chục triệu dòng: chuyển sang bảng tổng hợp theo giờ hoặc reporting-service (CQRS) – API giữ nguyên.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final Duration TTL = Duration.ofSeconds(60);
    static final int MAX_DAYS = 366;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final int TOP_EVENTS = 10;

    private static final Set<String> RECEIVED = Set.of("CONFIRMED", "REFUND_PENDING", "REFUNDED");
    private static final Set<String> REFUND = Set.of("REFUND_PENDING", "REFUNDED");
    private static final Set<String> OPEN = Set.of("PENDING", "AWAITING_PAYMENT");
    private static final Set<String> FAILED = Set.of("CANCELLED", "EXPIRED");

    private record Key(LocalDate from, LocalDate to, UUID eventId, boolean includeTest) {
    }

    private record Cached(Instant at, AdminStats stats) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();

    public AdminStats stats(LocalDate from, LocalDate to, UUID eventId, boolean includeTest) {
        LocalDate today = LocalDate.now(clock.withZone(VN));
        LocalDate t = to != null ? to : today;
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "Khoảng ngày tối đa " + MAX_DAYS + " ngày");
        }
        Key key = new Key(f, t, eventId, includeTest);
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(now)) {
            return hit.stats();
        }
        AdminStats fresh = compute(key, now);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();   // đơn giản, đủ dùng: vài admin, vài tổ hợp lọc
        }
        cache.put(key, new Cached(now, fresh));
        return fresh;
    }

    private AdminStats compute(Key k, Instant now) {
        Instant start = k.from().atStartOfDay(VN).toInstant();
        Instant end = k.to().plusDays(1).atStartOfDay(VN).toInstant();
        // Điều kiện ghép tĩnh (không "(:x is null or …)") để planner dùng đúng index.
        StringBuilder where = new StringBuilder("b.created_at >= :start and b.created_at < :end");
        if (k.eventId() != null) {
            where.append(" and b.event_id = :event");
        }
        if (!k.includeTest()) {
            where.append(" and not coalesce(e.hidden, false)");
        }
        String from = " from bookings b left join event_info e on e.event_id = b.event_id where " + where;
        Map<String, Object> params = new HashMap<>();
        params.put("start", Timestamp.from(start));
        params.put("end", Timestamp.from(end));
        if (k.eventId() != null) {
            params.put("event", k.eventId());
        }

        Map<String, AdminStats.StatusRow> byStatus = new LinkedHashMap<>();
        jdbc.sql("select b.status, count(*), coalesce(sum(b.ticket_count), 0), coalesce(sum(b.total), 0)" + from
                        + " group by b.status order by b.status")
                .params(params)
                .query((rs, i) -> byStatus.put(rs.getString(1), new AdminStats.StatusRow(rs.getLong(2), rs.getLong(3), rs.getLong(4))))
                .list();

        Map<String, Long> cancelReasons = new LinkedHashMap<>();
        jdbc.sql("select coalesce(b.cancel_reason, b.status), count(*)" + from
                        + " and b.status in ('CANCELLED', 'EXPIRED') group by 1 order by 2 desc")
                .params(params)
                .query((rs, i) -> cancelReasons.put(rs.getString(1), rs.getLong(2)))
                .list();

        Map<LocalDate, AdminStats.Day> days = new HashMap<>();
        jdbc.sql("""
                        select (b.created_at at time zone 'Asia/Ho_Chi_Minh')::date as d, count(*),
                               count(*) filter (where b.status = 'CONFIRMED'),
                               coalesce(sum(b.ticket_count) filter (where b.status = 'CONFIRMED'), 0),
                               coalesce(sum(b.total) filter (where b.status = 'CONFIRMED'), 0)""" + from + " group by d")
                .params(params)
                .query((rs, i) -> {
                    LocalDate d = rs.getObject(1, LocalDate.class);
                    return days.put(d, new AdminStats.Day(d, rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
                })
                .list();
        List<AdminStats.Day> daily = new ArrayList<>();
        for (LocalDate d = k.from(); !d.isAfter(k.to()); d = d.plusDays(1)) {
            daily.add(days.getOrDefault(d, new AdminStats.Day(d, 0, 0, 0, 0)));
        }

        List<AdminStats.EventRow> top = jdbc.sql("""
                        select b.event_id, e.title, e.starts_at, coalesce(e.hidden, false),
                               count(*), sum(b.ticket_count), sum(b.total)""" + from + " " + """
                         and b.status = 'CONFIRMED'
                         group by b.event_id, e.title, e.starts_at, e.hidden
                         order by sum(b.total) desc
                         limit :top""")
                .params(params).param("top", TOP_EVENTS)
                .query((rs, i) -> new AdminStats.EventRow(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(), rs.getBoolean(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7)))
                .list();

        return new AdminStats(k.from(), k.to(), k.eventId(), k.includeTest(), now,
                kpi(byStatus), byStatus, cancelReasons, daily, top);
    }

    private static AdminStats.Kpi kpi(Map<String, AdminStats.StatusRow> s) {
        long total = 0, open = 0, failed = 0, refundOrders = 0, gross = 0, refund = 0;
        for (var e : s.entrySet()) {
            var r = e.getValue();
            total += r.orders();
            if (OPEN.contains(e.getKey())) open += r.orders();
            if (FAILED.contains(e.getKey())) failed += r.orders();
            if (RECEIVED.contains(e.getKey())) gross += r.amount();
            if (REFUND.contains(e.getKey())) {
                refundOrders += r.orders();
                refund += r.amount();
            }
        }
        var confirmed = s.getOrDefault("CONFIRMED", new AdminStats.StatusRow(0, 0, 0));
        // Tỷ lệ chốt đơn: CONFIRMED / đơn đã có kết quả (bỏ đơn còn đang chờ – chưa biết thành hay hỏng).
        long closed = total - open;
        double conversion = closed == 0 ? 0 : (double) confirmed.orders() / closed;
        long aov = confirmed.orders() == 0 ? 0 : confirmed.amount() / confirmed.orders();
        return new AdminStats.Kpi(total, confirmed.orders(), open, failed, refundOrders, confirmed.tickets(),
                confirmed.amount(), gross, refund, conversion, aov);
    }
}
