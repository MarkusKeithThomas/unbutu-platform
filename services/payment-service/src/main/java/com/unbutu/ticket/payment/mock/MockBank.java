package com.unbutu.ticket.payment.mock;

import com.unbutu.ticket.payment.mock.fault.IpnPlan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Sổ sách phía "ngân hàng" giả lập (bảng mockbank_*). Merchant KHÔNG đọc trực tiếp – chỉ qua IPN / querydr. */
@Repository
@RequiredArgsConstructor
class MockBank {

    record Txn(String txnRef, long amount, String orderInfo, String bankCode, String returnUrl, String createDate,
               Instant expireAt, String status, String responseCode, String transactionNo, String payDate) {
    }

    record Delivery(long id, String txnRef, int attempt, String tamper, int fakeNetworkErrors, String faultNote) {
    }

    private static final String TXN_COLS = """
            txn_ref, amount, order_info, bank_code, return_url, create_date, expire_at, status, response_code,
            transaction_no, pay_date""";

    private final JdbcClient jdbc;

    private static Txn map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Txn(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getTimestamp(7).toInstant(), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11));
    }

    /** Khách mở trang thanh toán → ngân hàng biết giao dịch (OPEN). Mở lại → giữ nguyên. */
    void open(String txnRef, long amount, String orderInfo, String bankCode, String returnUrl, String createDate, Instant expireAt) {
        jdbc.sql("""
                        insert into mockbank_transactions (txn_ref, amount, order_info, bank_code, return_url, create_date, expire_at, status)
                        values (:r, :a, :oi, :bc, :ru, :cd, :exp, 'OPEN') on conflict (txn_ref) do nothing""")
                .param("r", txnRef).param("a", amount).param("oi", orderInfo).param("bc", bankCode).param("ru", returnUrl)
                .param("cd", createDate).param("exp", Timestamp.from(expireAt))
                .update();
    }

    Optional<Txn> find(String txnRef) {
        return jdbc.sql("select " + TXN_COLS + " from mockbank_transactions where txn_ref = :r").param("r", txnRef)
                .query((rs, i) -> map(rs)).optional();
    }

    /** OPEN → kết quả (khách bấm). false: đã có kết quả (bấm 2 lần / back rồi bấm lại). */
    boolean finish(String txnRef, String status, String rc, String txnNo, String payDate, Instant settleAt) {
        return jdbc.sql("""
                        update mockbank_transactions set status = :s, response_code = :rc, transaction_no = :no, pay_date = :pd,
                               settle_at = :settle, updated_at = now()
                        where txn_ref = :r and status = 'OPEN'""")
                .param("s", status).param("rc", rc).param("no", txnNo).param("pd", payDate)
                .param("settle", settleAt == null ? null : Timestamp.from(settleAt)).param("r", txnRef)
                .update() == 1;
    }

    /** PROCESSING tới hạn → SUCCESS (ngân hàng xử lý xong). */
    List<String> settleDue(int limit) {
        return jdbc.sql("""
                        update mockbank_transactions set status = 'SUCCESS', response_code = '00', updated_at = now()
                        where txn_ref in (select txn_ref from mockbank_transactions
                                          where status = 'PROCESSING' and settle_at <= now() limit :lim for update skip locked)
                        returning txn_ref""")
                .param("lim", limit).query(String.class).list();
    }

    void schedule(String txnRef, IpnPlan.Delivery d, String faults) {
        jdbc.sql("""
                        insert into mockbank_ipn_deliveries (txn_ref, due_at, tamper, fake_network_errors, fault_note)
                        values (:r, now() + make_interval(secs => :delay), :t, :n, :note)""")
                .param("r", txnRef).param("delay", d.delay.toMillis() / 1000.0).param("t", d.tamper.name())
                .param("n", d.fakeNetworkErrors).param("note", faults)
                .update();
    }

    /** Giành các lần gửi đến hạn (SKIP LOCKED → nhiều instance không gửi trùng 1 lần gửi). */
    List<Delivery> claimDue(int limit) {
        return jdbc.sql("""
                        update mockbank_ipn_deliveries set status = 'SENDING'
                        where id in (select id from mockbank_ipn_deliveries where status = 'SCHEDULED' and due_at <= now()
                                     order by due_at limit :lim for update skip locked)
                        returning id, txn_ref, attempt, tamper, fake_network_errors, fault_note""")
                .param("lim", limit)
                .query((rs, i) -> new Delivery(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getString(6)))
                .list();
    }

    void delivered(long id, String result) {
        jdbc.sql("update mockbank_ipn_deliveries set status = 'DELIVERED', attempt = attempt + 1, last_result = :res where id = :id")
                .param("res", trim(result)).param("id", id).update();
    }

    void retryLater(long id, long delaySeconds, String result, boolean consumedFakeError) {
        jdbc.sql("""
                        update mockbank_ipn_deliveries set status = 'SCHEDULED', attempt = attempt + 1, last_result = :res,
                               due_at = now() + make_interval(secs => :d),
                               fake_network_errors = greatest(0, fake_network_errors - :dec)
                        where id = :id""")
                .param("res", trim(result)).param("d", delaySeconds).param("dec", consumedFakeError ? 1 : 0).param("id", id)
                .update();
    }

    void gaveUp(long id, String result) {
        jdbc.sql("update mockbank_ipn_deliveries set status = 'GAVE_UP', attempt = attempt + 1, last_result = :res where id = :id")
                .param("res", trim(result)).param("id", id).update();
    }

    private static String trim(String s) {
        return s == null ? null : s.length() > 200 ? s.substring(0, 200) : s;
    }
}
