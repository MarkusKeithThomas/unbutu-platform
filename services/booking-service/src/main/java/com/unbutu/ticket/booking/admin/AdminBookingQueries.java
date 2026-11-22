package com.unbutu.ticket.booking.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tra cứu đơn cho admin (ADM-2). Chỉ đọc. Keyset (created_at, id) – không COUNT, không OFFSET → trang 1 hay trang 1000
 * cùng chi phí. Tìm kiếm chỉ khớp CHÍNH XÁC trên cột có index (mã đơn, email, SĐT, id) – không LIKE '%…%'.
 */
@Repository
@RequiredArgsConstructor
class AdminBookingQueries {

    static final Set<String> STATUSES =
            Set.of("PENDING", "AWAITING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED", "REFUND_PENDING", "REFUNDED");
    private static final Pattern CODE = Pattern.compile("(?i)ORD-\\d+");
    private static final Pattern UUID_RE = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    record EventRef(UUID id, String title, Instant startsAt, boolean hidden) {
    }

    record Buyer(String name, String email, String phone) {
    }

    record BookingRow(UUID id, String code, String status, String cancelReason, UUID userId, EventRef event, Buyer buyer,
                      int ticketCount, long subtotal, long fee, long total, String paymentMethod,
                      Instant createdAt, Instant expiresAt, Instant confirmedAt, Instant updatedAt) {
    }

    record Line(UUID ticketTypeId, String name, long unitPrice, int quantity, long amount) {
    }

    record Page(List<BookingRow> items, String nextCursor) {
    }

    private static final String SELECT = """
            select b.id, b.code, b.status, b.cancel_reason, b.user_id, b.event_id, e.title, e.starts_at,
                   coalesce(e.hidden, false) as hidden, b.buyer_name, b.buyer_email, b.buyer_phone, b.ticket_count,
                   b.subtotal, b.fee, b.total, b.payment_method, b.created_at, b.expires_at, b.confirmed_at, b.updated_at
              from bookings b left join event_info e on e.event_id = b.event_id
            """;

    private final JdbcClient jdbc;

    Page list(String status, UUID eventId, String q, Instant from, Instant to, boolean includeTest, Cursor cursor, int size) {
        StringBuilder where = new StringBuilder(" where true");
        Map<String, Object> params = new HashMap<>();
        if (status != null) {
            where.append(" and b.status = :status");
            params.put("status", status);
        }
        if (eventId != null) {
            where.append(" and b.event_id = :event");
            params.put("event", eventId);
        }
        if (from != null) {
            where.append(" and b.created_at >= :from");
            params.put("from", Timestamp.from(from));
        }
        if (to != null) {
            where.append(" and b.created_at < :to");
            params.put("to", Timestamp.from(to));
        }
        if (!includeTest) {
            where.append(" and not coalesce(e.hidden, false)");
        }
        if (q != null && !q.isBlank()) {
            String s = q.trim();
            if (CODE.matcher(s).matches()) {
                where.append(" and b.code = :q");
                params.put("q", s.toUpperCase());
            } else if (UUID_RE.matcher(s).matches()) {
                where.append(" and (b.id = :qid or b.user_id = :qid)");
                params.put("qid", UUID.fromString(s));
            } else if (s.contains("@")) {
                where.append(" and lower(b.buyer_email) = :q");
                params.put("q", s.toLowerCase());
            } else {
                where.append(" and b.buyer_phone = :q");
                params.put("q", s.replaceAll("[\\s.-]", ""));
            }
        }
        if (cursor != null) {
            where.append(" and (b.created_at, b.id) < (:cAt, :cId)");
            params.put("cAt", Timestamp.from(cursor.createdAt()));
            params.put("cId", cursor.id());
        }
        params.put("lim", size + 1);
        List<BookingRow> rows = new ArrayList<>(jdbc.sql(SELECT + where + " order by b.created_at desc, b.id desc limit :lim")
                .params(params)
                .query((rs, i) -> map(rs))
                .list());
        String next = null;
        if (rows.size() > size) {
            rows.remove(size);
            BookingRow last = rows.get(size - 1);
            next = new Cursor(last.createdAt(), last.id()).encode();
        }
        return new Page(rows, next);
    }

    Optional<BookingRow> find(UUID id) {
        return jdbc.sql(SELECT + " where b.id = :id").param("id", id).query((rs, i) -> map(rs)).optional();
    }

    List<Line> lines(UUID bookingId) {
        return jdbc.sql("""
                        select ticket_type_id, name, unit_price, quantity from booking_items
                         where booking_id = :b order by name""")
                .param("b", bookingId)
                .query((rs, i) -> new Line(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getInt(4),
                        rs.getLong(3) * rs.getInt(4)))
                .list();
    }

    private static BookingRow map(ResultSet rs) throws SQLException {
        return new BookingRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("status"),
                rs.getString("cancel_reason"), rs.getObject("user_id", UUID.class),
                new EventRef(rs.getObject("event_id", UUID.class), rs.getString("title"), ts(rs, "starts_at"), rs.getBoolean("hidden")),
                new Buyer(rs.getString("buyer_name"), rs.getString("buyer_email"), rs.getString("buyer_phone")),
                rs.getInt("ticket_count"), rs.getLong("subtotal"), rs.getLong("fee"), rs.getLong("total"),
                rs.getString("payment_method"), ts(rs, "created_at"), ts(rs, "expires_at"), ts(rs, "confirmed_at"),
                ts(rs, "updated_at"));
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
}
