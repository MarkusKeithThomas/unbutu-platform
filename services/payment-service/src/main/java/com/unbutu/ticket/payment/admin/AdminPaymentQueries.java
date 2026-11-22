package com.unbutu.ticket.payment.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tra cứu thanh toán cho admin (ADM-3). Chỉ đọc. Danh sách: keyset (created_at, id) trên index – không COUNT, không OFFSET.
 * "Treo" = PENDING quá {@link #STUCK_AFTER}: job đối soát (querydr, mỗi 1′, sau 2′) lẽ ra đã chốt → cần người xem.
 */
@Repository
@RequiredArgsConstructor
class AdminPaymentQueries {

    static final Duration STUCK_AFTER = Duration.ofMinutes(15);
    static final Set<String> STATUSES = Set.of("PENDING", "SUCCEEDED", "FAILED", "REFUNDED");

    record PaymentRow(UUID id, UUID bookingId, String bookingCode, int attempt, String txnRef, long amount, String method,
                      String status, boolean stuck, String providerTxnNo, String providerResponseCode,
                      Instant expireAt, Instant paidAt, Instant refundedAt, Instant createdAt, Instant updatedAt,
                      LastCheck lastCheck) {
    }

    /** Lần đối soát gần nhất (reconciliation_log) – null nếu chưa đối soát. */
    record LastCheck(String gatewayCode, String gatewayStatus, String action, String note, Instant checkedAt) {
    }

    record Page(List<PaymentRow> items, String nextCursor) {
    }

    record Summary(Instant from, Instant to, Map<String, Long> countByStatus, Map<String, Long> amountByStatus,
                   Map<String, Long> failureCodes, long stuck, Instant generatedAt) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    Page list(String status, boolean stuckOnly, UUID bookingId, String q, Cursor cursor, int size) {
        Instant now = clock.instant();
        StringBuilder where = new StringBuilder("true");
        Map<String, Object> params = new HashMap<>();
        if (stuckOnly) {
            where.append(" and p.status = 'PENDING' and p.created_at < :stuckBefore");
            params.put("stuckBefore", Timestamp.from(now.minus(STUCK_AFTER)));
        } else if (status != null) {
            where.append(" and p.status = :status");
            params.put("status", status);
        }
        if (bookingId != null) {
            where.append(" and p.booking_id = :booking");
            params.put("booking", bookingId);
        }
        if (q != null && !q.isBlank()) {
            // Mã đơn (ORD-100000) hoặc mã giao dịch (ORD-100000-1) – khớp chính xác, chạy trên index.
            where.append(" and (p.booking_code = :q or p.txn_ref = :q)");
            params.put("q", q.trim().toUpperCase());
        }
        if (cursor != null) {
            where.append(" and (p.created_at, p.id) < (:cAt, :cId)");
            params.put("cAt", Timestamp.from(cursor.createdAt()));
            params.put("cId", cursor.id());
        }
        params.put("lim", size + 1);
        params.put("stuckAt", Timestamp.from(now.minus(STUCK_AFTER)));
        List<PaymentRow> rows = new ArrayList<>(jdbc.sql("""
                        select p.id, p.booking_id, p.booking_code, p.attempt, p.txn_ref, p.amount, p.method, p.status,
                               (p.status = 'PENDING' and p.created_at < :stuckAt) as stuck,
                               p.provider_txn_no, p.provider_response_code, p.expire_at, p.paid_at, p.refunded_at,
                               p.created_at, p.updated_at,
                               r.gateway_code, r.gateway_status, r.action, r.note, r.checked_at
                          from payments p
                          left join lateral (select gateway_code, gateway_status, action, note, checked_at
                                               from reconciliation_log l where l.payment_id = p.id
                                              order by checked_at desc limit 1) r on true
                        """ + " where " + where + " order by p.created_at desc, p.id desc limit :lim")
                .params(params)
                .query((rs, i) -> map(rs))
                .list());
        String next = null;
        if (rows.size() > size) {
            rows.remove(size);
            PaymentRow last = rows.get(size - 1);
            next = new Cursor(last.createdAt(), last.id()).encode();
        }
        return new Page(rows, next);
    }

    Summary summary(Instant from, Instant to) {
        Map<String, Object> params = Map.of("from", Timestamp.from(from), "to", Timestamp.from(to));
        Map<String, Long> count = new LinkedHashMap<>();
        Map<String, Long> amount = new LinkedHashMap<>();
        jdbc.sql("""
                        select status, count(*), coalesce(sum(amount), 0) from payments
                         where created_at >= :from and created_at < :to group by status order by status""")
                .params(params)
                .query((rs, i) -> {
                    count.put(rs.getString(1), rs.getLong(2));
                    return amount.put(rs.getString(1), rs.getLong(3));
                })
                .list();
        Map<String, Long> codes = new LinkedHashMap<>();
        jdbc.sql("""
                        select coalesce(provider_response_code, '?'), count(*) from payments
                         where status = 'FAILED' and created_at >= :from and created_at < :to
                         group by 1 order by 2 desc limit 20""")
                .params(params)
                .query((rs, i) -> codes.put(rs.getString(1), rs.getLong(2)))
                .list();
        Instant now = clock.instant();
        // Treo: không giới hạn khoảng ngày – PENDING cũ bao nhiêu cũng cần xử lý (index một phần ix_payments_pending_created).
        long stuck = jdbc.sql("select count(*) from payments where status = 'PENDING' and created_at < :t")
                .param("t", Timestamp.from(now.minus(STUCK_AFTER))).query(Long.class).single();
        return new Summary(from, to, count, amount, codes, stuck, now);
    }

    private static PaymentRow map(ResultSet rs) throws SQLException {
        Timestamp checked = rs.getTimestamp("checked_at");
        LastCheck last = checked == null ? null : new LastCheck(rs.getString("gateway_code"), rs.getString("gateway_status"),
                rs.getString("action"), rs.getString("note"), checked.toInstant());
        return new PaymentRow(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getString("booking_code"), rs.getInt("attempt"), rs.getString("txn_ref"), rs.getLong("amount"),
                rs.getString("method"), rs.getString("status"), rs.getBoolean("stuck"), rs.getString("provider_txn_no"),
                rs.getString("provider_response_code"), ts(rs, "expire_at"), ts(rs, "paid_at"), ts(rs, "refunded_at"),
                ts(rs, "created_at"), ts(rs, "updated_at"), last);
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
}
