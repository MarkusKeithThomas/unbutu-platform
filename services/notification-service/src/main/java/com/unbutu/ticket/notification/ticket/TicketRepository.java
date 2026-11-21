package com.unbutu.ticket.notification.ticket;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class TicketRepository {

    record Row(UUID id, UUID bookingId, String bookingCode, UUID userId, int seq, UUID eventId, String eventTitle,
               String venue, Instant startsAt, UUID ticketTypeId, String ticketTypeName, long unitPrice, String status,
               String qr, Instant issuedAt) {
    }

    private static final String COLS = """
            id, booking_id, booking_code, user_id, seq, event_id, event_title, venue, starts_at, ticket_type_id,
            ticket_type_name, unit_price, status, qr, issued_at""";

    private final JdbcClient jdbc;

    private static Row map(ResultSet rs, int i) throws SQLException {
        Timestamp starts = rs.getTimestamp("starts_at");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
                rs.getObject("user_id", UUID.class), rs.getInt("seq"), rs.getObject("event_id", UUID.class),
                rs.getString("event_title"), rs.getString("venue"), starts == null ? null : starts.toInstant(),
                rs.getObject("ticket_type_id", UUID.class), rs.getString("ticket_type_name"), rs.getLong("unit_price"),
                rs.getString("status"), rs.getString("qr"), rs.getTimestamp("issued_at").toInstant());
    }

    /** true nếu thêm mới; event lặp (đã phát) → false, không đổi gì. */
    boolean insert(Row r) {
        return jdbc.sql("""
                        insert into tickets (id, booking_id, booking_code, user_id, seq, event_id, event_title, venue, starts_at,
                                             ticket_type_id, ticket_type_name, unit_price, status, qr, issued_at)
                        values (:id, :b, :code, :u, :seq, :e, :title, :venue, :starts, :tt, :ttName, :price, 'VALID', :qr, :issued)
                        on conflict (booking_id, seq) do nothing""")
                .param("id", r.id()).param("b", r.bookingId()).param("code", r.bookingCode()).param("u", r.userId())
                .param("seq", r.seq()).param("e", r.eventId()).param("title", r.eventTitle()).param("venue", r.venue())
                .param("starts", r.startsAt() == null ? null : Timestamp.from(r.startsAt()))
                .param("tt", r.ticketTypeId()).param("ttName", r.ticketTypeName()).param("price", r.unitPrice())
                .param("qr", r.qr()).param("issued", Timestamp.from(r.issuedAt()))
                .update() == 1;
    }

    /** Vé của 1 đơn – chỉ của chủ đơn (lọc user_id ngay trong SQL: đơn người khác = như chưa có vé). */
    List<Row> byBooking(UUID bookingId, UUID userId) {
        return jdbc.sql("select " + COLS + " from tickets where booking_id = :b and user_id = :u order by seq")
                .param("b", bookingId).param("u", userId).query(TicketRepository::map).list();
    }

    /** Slice: đọc size+1 dòng để biết còn trang sau – không COUNT(*). */
    List<Row> mine(UUID userId, int page, int size) {
        return jdbc.sql("select " + COLS + " from tickets where user_id = :u order by issued_at desc, seq limit :lim offset :off")
                .param("u", userId).param("lim", size + 1).param("off", page * size).query(TicketRepository::map).list();
    }
}
