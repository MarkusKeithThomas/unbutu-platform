package com.unbutu.ticket.booking.booking;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL tường minh: mọi chuyển trạng thái là {@code UPDATE … WHERE status = <cũ>} → chạy 2 lần chỉ tác dụng 1 lần. */
@Repository
@RequiredArgsConstructor
class BookingRepository {

    /** Đơn đang "giữ chỗ" của user cho 1 sự kiện → tính vào giới hạn 4 vé. PAY-1 thêm AWAITING_PAYMENT, CONFIRMED. */
    static final List<String> COUNTED_STATUSES = List.of("PENDING", "AWAITING_PAYMENT", "CONFIRMED");

    record Row(UUID id, String code, UUID userId, UUID eventId, String status, String cancelReason, int ticketCount,
               long subtotal, long fee, long total, String requestHash, Instant expiresAt, Instant createdAt) {
    }

    record ItemRow(UUID bookingId, UUID ticketTypeId, String name, long unitPrice, int quantity) {
    }

    record Expired(UUID id, UUID eventId) {
    }

    private static final String COLS = """
            id, code, user_id, event_id, status, cancel_reason, ticket_count, subtotal, fee, total, request_hash,
            expires_at, created_at""";

    private final JdbcClient jdbc;

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getString("code"), rs.getObject("user_id", UUID.class),
                rs.getObject("event_id", UUID.class), rs.getString("status"), rs.getString("cancel_reason"),
                rs.getInt("ticket_count"), rs.getLong("subtotal"), rs.getLong("fee"), rs.getLong("total"),
                rs.getString("request_hash"), rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant());
    }

    /** Khoá theo (user, sự kiện) tới hết transaction: 2 request đồng thời của 1 người không cùng lọt giới hạn 4 vé. */
    void lockUserEvent(UUID userId, UUID eventId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:k, 0))").param("k", userId + ":" + eventId)
                .query(Object.class).single();
    }

    int countedTickets(UUID userId, UUID eventId) {
        return jdbc.sql("""
                        select coalesce(sum(ticket_count), 0) from bookings
                        where user_id = :u and event_id = :e and status in (:st)""")
                .param("u", userId).param("e", eventId).param("st", COUNTED_STATUSES)
                .query(Integer.class).single();
    }

    Optional<Row> findByIdempotencyKey(UUID userId, String key) {
        return jdbc.sql("select " + COLS + " from bookings where user_id = :u and idempotency_key = :k")
                .param("u", userId).param("k", key).query((rs, i) -> map(rs)).optional();
    }

    Optional<Row> findOwned(UUID id, UUID userId) {
        return jdbc.sql("select " + COLS + " from bookings where id = :id and user_id = :u")
                .param("id", id).param("u", userId).query((rs, i) -> map(rs)).optional();
    }

    /** Slice: đọc size+1 dòng để biết còn trang sau – không COUNT(*). */
    List<Row> findMine(UUID userId, int page, int size) {
        return jdbc.sql("select " + COLS + " from bookings where user_id = :u order by created_at desc limit :lim offset :off")
                .param("u", userId).param("lim", size + 1).param("off", page * size).query((rs, i) -> map(rs)).list();
    }

    void insert(UUID id, UUID userId, UUID eventId, int ticketCount, long subtotal, long fee, long total,
                String idempotencyKey, String requestHash, Instant expiresAt, List<ItemRow> items) {
        jdbc.sql("""
                        insert into bookings (id, code, user_id, event_id, status, ticket_count, subtotal, fee, total,
                                              idempotency_key, request_hash, expires_at)
                        values (:id, 'ORD-' || nextval('booking_code_seq'), :u, :e, 'PENDING', :n, :sub, :fee, :total,
                                :key, :hash, :exp)""")
                .param("id", id).param("u", userId).param("e", eventId).param("n", ticketCount).param("sub", subtotal)
                .param("fee", fee).param("total", total).param("key", idempotencyKey).param("hash", requestHash)
                .param("exp", Timestamp.from(expiresAt))
                .update();
        for (ItemRow it : items) {
            jdbc.sql("""
                            insert into booking_items (booking_id, ticket_type_id, name, unit_price, quantity)
                            values (:b, :tt, :name, :price, :q)""")
                    .param("b", id).param("tt", it.ticketTypeId()).param("name", it.name())
                    .param("price", it.unitPrice()).param("q", it.quantity())
                    .update();
        }
    }

    Map<UUID, List<ItemRow>> items(Collection<UUID> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        select booking_id, ticket_type_id, name, unit_price, quantity from booking_items
                        where booking_id in (:ids) order by unit_price desc""")
                .param("ids", bookingIds)
                .query((rs, i) -> new ItemRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getLong(4), rs.getInt(5)))
                .list().stream().collect(Collectors.groupingBy(ItemRow::bookingId));
    }

    Optional<Row> findById(UUID id) {
        return jdbc.sql("select " + COLS + " from bookings where id = :id").param("id", id).query((rs, i) -> map(rs)).optional();
    }

    /** Bước 2 → cổng: lưu người mua + phương thức, PENDING → AWAITING_PAYMENT (bấm lại khi đang chờ: cập nhật). Còn hạn mới được. */
    boolean startPayment(UUID id, UUID userId, String name, String email, String phone, String method) {
        return jdbc.sql("""
                        update bookings set status = 'AWAITING_PAYMENT', buyer_name = :n, buyer_email = :e, buyer_phone = :p,
                               payment_method = :m, updated_at = now()
                        where id = :id and user_id = :u and status in ('PENDING', 'AWAITING_PAYMENT') and expires_at > now()""")
                .param("n", name).param("e", email).param("p", phone).param("m", method).param("id", id).param("u", userId)
                .update() == 1;
    }

    /** Thanh toán thất bại / khách huỷ ở cổng → cho thanh toán lại (nếu còn hạn, job hết hạn lo phần còn lại). */
    void backToPending(UUID id) {
        jdbc.sql("update bookings set status = 'PENDING', updated_at = now() where id = :id and status = 'AWAITING_PAYMENT'")
                .param("id", id).update();
    }

    /** Đã thu tiền + đã chốt vé ở inventory. Nhận cả đơn đã EXPIRED/CANCELLED (trả muộn mà vẫn còn vé). */
    boolean confirm(UUID id) {
        return jdbc.sql("""
                        update bookings set status = 'CONFIRMED', confirmed_at = now(), updated_at = now()
                        where id = :id and status in ('PENDING', 'AWAITING_PAYMENT', 'EXPIRED', 'CANCELLED')""")
                .param("id", id).update() == 1;
    }

    void refundPending(UUID id, String reason) {
        jdbc.sql("""
                        update bookings set status = 'REFUND_PENDING', cancel_reason = :r, updated_at = now()
                        where id = :id and status in ('PENDING', 'AWAITING_PAYMENT', 'EXPIRED', 'CANCELLED')""")
                .param("r", reason).param("id", id).update();
    }

    void refunded(UUID id) {
        jdbc.sql("update bookings set status = 'REFUNDED', updated_at = now() where id = :id and status = 'REFUND_PENDING'")
                .param("id", id).update();
    }

    void setExpiresAt(UUID id, Instant expiresAt) {
        jdbc.sql("update bookings set expires_at = :exp, updated_at = now() where id = :id and status = 'PENDING'")
                .param("exp", Timestamp.from(expiresAt)).param("id", id).update();
    }

    /** PENDING/AWAITING_PAYMENT → CANCELLED. true nếu đổi được. Lỡ trả tiền sau khi huỷ → quyết toán xử lý như trả muộn. */
    boolean cancelPending(UUID id, String reason) {
        return jdbc.sql("""
                        update bookings set status = 'CANCELLED', cancel_reason = :r, updated_at = now()
                        where id = :id and status in ('PENDING', 'AWAITING_PAYMENT')""")
                .param("r", reason).param("id", id).update() == 1;
    }

    /**
     * Quá hạn → EXPIRED, theo lô. SKIP LOCKED: nhiều instance chạy job cùng lúc không tranh nhau.
     * AWAITING_PAYMENT có thêm ân hạn (khách đang ở trang ngân hàng / IPN đang tới).
     */
    List<Expired> expireDue(int limit, java.time.Duration paymentGrace) {
        return jdbc.sql("""
                        update bookings set status = 'EXPIRED', updated_at = now()
                        where id in (select id from bookings
                                     where (status = 'PENDING' and expires_at < now())
                                        or (status = 'AWAITING_PAYMENT' and expires_at < now() - make_interval(secs => :grace))
                                     order by expires_at limit :lim for update skip locked)
                        returning id, event_id""")
                .param("lim", limit).param("grace", paymentGrace.toSeconds())
                .query((rs, i) -> new Expired(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list();
    }
}
