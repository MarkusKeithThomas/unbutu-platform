package com.unbutu.ticket.payment.reconcile;

import com.unbutu.ticket.payment.PaymentService;
import com.unbutu.ticket.payment.vnpay.QueryDrClient;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ĐỐI SOÁT: giao dịch PENDING quá {@code pending-after} mà chưa có IPN → hỏi querydr → áp kết quả bằng ĐÚNG đường IPN
 * ({@link PaymentService#settleFromReconcile}). Chạy lại an toàn: áp có điều kiện, IPN tới cùng lúc → 1 bên thắng.
 * Mỗi lần hỏi ghi reconciliation_log (xem lại / điều tra). Gọi mạng ngoài transaction.
 *
 * <pre>
 * querydr 00 + status 00 (+ đúng số tiền) → SUCCEEDED   · sai số tiền → MISMATCH (không đổi, cảnh báo)
 * querydr 00 + status 02                    → FAILED (mã lỗi của ngân hàng)
 * querydr 00 + status 01 chưa có mã GD      → khách chưa trả: quá hạn giữ + abandon-after → FAILED(ABANDONED), chưa → WAIT
 * querydr 00 + status 01 có mã GD           → ngân hàng đang xử lý → WAIT
 * querydr 91                                → khách chưa mở trang ngân hàng: quá hạn → FAILED(NOT_PAID), chưa → WAIT
 * 97 / 99 / lỗi mạng / chữ ký phản hồi sai   → ERROR, thử lại lượt sau
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.reconcile.enabled", havingValue = "true", matchIfMissing = true)
class ReconciliationJob {

    private final PaymentService payments;
    private final QueryDrClient querydr;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration pendingAfter;
    private final Duration abandonAfter;
    private final int batch;

    ReconciliationJob(PaymentService payments, QueryDrClient querydr, JdbcClient jdbc, Clock clock,
                      @Value("${payment.reconcile.pending-after}") Duration pendingAfter,
                      @Value("${payment.reconcile.abandon-after}") Duration abandonAfter,
                      @Value("${payment.reconcile.batch:100}") int batch) {
        this.payments = payments;
        this.querydr = querydr;
        this.jdbc = jdbc;
        this.clock = clock;
        this.pendingAfter = pendingAfter;
        this.abandonAfter = abandonAfter;
        this.batch = batch;
    }

    @Scheduled(fixedDelayString = "${payment.reconcile.interval:1m}", initialDelayString = "${payment.reconcile.interval:1m}")
    void run() {
        var now = clock.instant();
        var candidates = payments.pendingOlderThan(now.minus(pendingAfter), batch);
        if (candidates.isEmpty()) {
            return;
        }
        int fixed = 0;
        for (var p : candidates) {
            String action;
            String note = null;
            QueryDrClient.Result r = null;
            try {
                r = querydr.query(p.txnRef(), p.vnpCreateDate(), "Thanh toan don " + p.bookingCode());
                boolean overdue = now.isAfter(p.expireAt().plus(abandonAfter));
                if (!r.signatureValid() && !"91".equals(r.responseCode())) {
                    action = "ERROR";
                    note = "chữ ký phản hồi querydr sai / lỗi " + r.responseCode();
                } else if ("00".equals(r.responseCode()) && "00".equals(r.transactionStatus())) {
                    if (r.amount() != p.amount()) {
                        action = "MISMATCH";
                        note = "ngân hàng " + r.amount() + " đ ≠ đơn " + p.amount() + " đ – cần xử lý tay";
                        log.error("ĐỐI SOÁT {}: {}", p.txnRef(), note);
                    } else {
                        action = payments.settleFromReconcile(p, true, r.transactionNo(), "00") ? "SUCCEEDED" : "ALREADY_FINAL";
                    }
                } else if ("00".equals(r.responseCode()) && "02".equals(r.transactionStatus())) {
                    String rc = r.transactionResponseCode() == null ? "02" : r.transactionResponseCode();
                    action = payments.settleFromReconcile(p, false, r.transactionNo(), rc) ? "FAILED" : "ALREADY_FINAL";
                } else if ("00".equals(r.responseCode()) && r.transactionNo() != null) {
                    action = "WAIT";
                    note = "ngân hàng đang xử lý";
                } else if (("00".equals(r.responseCode()) || "91".equals(r.responseCode())) && overdue) {
                    String why = "91".equals(r.responseCode()) ? "NOT_PAID" : "ABANDONED";
                    action = payments.settleFromReconcile(p, false, null, why) ? "FAILED" : "ALREADY_FINAL";
                    note = "91".equals(r.responseCode()) ? "khách chưa mở trang ngân hàng" : "khách bỏ ngang trên trang ngân hàng";
                } else if ("00".equals(r.responseCode()) || "91".equals(r.responseCode())) {
                    action = "WAIT";
                    note = "chưa quá hạn giữ vé";
                } else {
                    action = "ERROR";
                    note = "querydr " + r.responseCode();
                }
            } catch (Exception e) {
                action = "ERROR";
                note = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if ("SUCCEEDED".equals(action) || "FAILED".equals(action)) {
                fixed++;
                log.warn("ĐỐI SOÁT {} ({}): {} – IPN không tới / không hợp lệ, đã sửa theo querydr", p.txnRef(), p.bookingCode(), action);
            }
            jdbc.sql("""
                            insert into reconciliation_log (payment_id, txn_ref, gateway_code, gateway_status, action, note)
                            values (:p, :t, :c, :s, :a, :n)""")
                    .param("p", p.id()).param("t", p.txnRef()).param("c", r == null ? null : r.responseCode())
                    .param("s", r == null ? null : r.transactionStatus()).param("a", action)
                    .param("n", note == null ? null : note.length() > 300 ? note.substring(0, 300) : note)
                    .update();
        }
        log.info("Đối soát: {} giao dịch treo, sửa {}", candidates.size(), fixed);
    }
}
