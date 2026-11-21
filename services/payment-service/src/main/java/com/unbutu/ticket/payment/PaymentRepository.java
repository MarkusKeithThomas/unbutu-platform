package com.unbutu.ticket.payment;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Mọi chuyển trạng thái là UPDATE … WHERE status = <cũ> → IPN đến 2 lần chỉ tác dụng 1 lần. */
@Repository
@RequiredArgsConstructor
class PaymentRepository {

    record Row(UUID id, UUID bookingId, String bookingCode, int attempt, String txnRef, long amount, String method,
               String status, Instant expireAt) {
    }

    private static final String COLS = "id, booking_id, booking_code, attempt, txn_ref, amount, method, status, expire_at";

    private final JdbcClient jdbc;

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
                rs.getInt("attempt"), rs.getString("txn_ref"), rs.getLong("amount"), rs.getString("method"),
                rs.getString("status"), rs.getTimestamp("expire_at").toInstant());
    }

    /** Khoá theo đơn: 2 lần bấm "Xác nhận" cùng lúc không tạo 2 giao dịch. */
    void lockBooking(UUID bookingId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:k, 0))").param("k", "payment:" + bookingId)
                .query(Object.class).single();
    }

    Optional<Row> latest(UUID bookingId) {
        return jdbc.sql("select " + COLS + " from payments where booking_id = :b order by attempt desc limit 1")
                .param("b", bookingId).query((rs, i) -> map(rs)).optional();
    }

    Optional<Row> byStatus(UUID bookingId, String status) {
        return jdbc.sql("select " + COLS + " from payments where booking_id = :b and status = :s order by attempt desc limit 1")
                .param("b", bookingId).param("s", status).query((rs, i) -> map(rs)).optional();
    }

    Optional<Row> byTxnRef(String txnRef) {
        return jdbc.sql("select " + COLS + " from payments where txn_ref = :t").param("t", txnRef)
                .query((rs, i) -> map(rs)).optional();
    }

    void insert(Row r) {
        jdbc.sql("""
                        insert into payments (id, booking_id, booking_code, attempt, txn_ref, amount, method, status, expire_at)
                        values (:id, :b, :code, :att, :ref, :amt, :m, 'PENDING', :exp)""")
                .param("id", r.id()).param("b", r.bookingId()).param("code", r.bookingCode()).param("att", r.attempt())
                .param("ref", r.txnRef()).param("amt", r.amount()).param("m", r.method())
                .param("exp", Timestamp.from(r.expireAt()))
                .update();
    }

    /** PENDING → SUCCEEDED/FAILED. false: đã xử lý trước đó (IPN lặp lại). */
    boolean complete(UUID id, String status, String providerTxnNo, String responseCode) {
        return jdbc.sql("""
                        update payments set status = :s, provider_txn_no = :tx, provider_response_code = :rc,
                               paid_at = case when :s = 'SUCCEEDED' then now() end, updated_at = now()
                        where id = :id and status = 'PENDING'""")
                .param("s", status).param("tx", providerTxnNo).param("rc", responseCode).param("id", id)
                .update() == 1;
    }

    /** Đơn có attempt PENDING đã quá hạn → coi như FAILED để tạo attempt mới được. */
    void failPending(UUID id, String responseCode) {
        jdbc.sql("""
                        update payments set status = 'FAILED', provider_response_code = :rc, updated_at = now()
                        where id = :id and status = 'PENDING'""")
                .param("rc", responseCode).param("id", id).update();
    }

    boolean refund(UUID id) {
        return jdbc.sql("""
                        update payments set status = 'REFUNDED', refunded_at = now(), updated_at = now()
                        where id = :id and status = 'SUCCEEDED'""")
                .param("id", id).update() == 1;
    }
}
