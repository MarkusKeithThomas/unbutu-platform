package com.unbutu.ticket.payment;

import com.unbutu.ticket.events.payment.PaymentEvents;
import com.unbutu.ticket.outbox.Outbox;
import com.unbutu.ticket.payment.vnpay.VnpaySigner;
import com.unbutu.ticket.payment.web.PaymentException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Thanh toán theo giao thức VNPay 2.1.0. IPN (server→server, có chữ ký) là NGUỒN SỰ THẬT; return URL chỉ để
 * đưa trình duyệt về UI. Kết quả → Outbox payment.result.v1 trong cùng transaction với đổi trạng thái.
 */
@Slf4j
@Service
public class PaymentService {

    /** VNPay dùng giờ GMT+7, định dạng yyyyMMddHHmmss. */
    public static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final Map<String, String> BANK_CODE = Map.of("VNBANK", "VNBANK", "VNPAYQR", "VNPAYQR", "INTCARD", "INTCARD");

    public record Created(UUID paymentId, String paymentUrl, Instant expireAt) {
    }

    /** Trả về cho cổng (VNPay yêu cầu HTTP 200 + JSON này cho MỌI trường hợp). */
    public record IpnResponse(String RspCode, String Message) {
    }

    private final PaymentRepository payments;
    private final VnpaySigner signer;
    private final Outbox outbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String tmnCode;
    private final String payUrl;
    private final String publicBaseUrl;
    private final String uiBaseUrl;

    PaymentService(PaymentRepository payments, VnpaySigner signer, Outbox outbox, TransactionTemplate tx, Clock clock,
                   @Value("${payment.vnpay.tmn-code}") String tmnCode, @Value("${payment.vnpay.pay-url}") String payUrl,
                   @Value("${payment.public-base-url}") String publicBaseUrl, @Value("${payment.ui-base-url}") String uiBaseUrl) {
        this.payments = payments;
        this.signer = signer;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
        this.tmnCode = tmnCode;
        this.payUrl = payUrl;
        this.publicBaseUrl = publicBaseUrl;
        this.uiBaseUrl = uiBaseUrl;
    }

    /**
     * booking gọi khi khách bấm "Xác nhận thanh toán". Idempotent theo đơn: còn attempt PENDING chưa hết hạn, cùng số tiền
     * và phương thức → trả lại đúng URL cũ (bấm lại không tạo giao dịch thứ 2). Đã trả tiền → 409.
     */
    public Created create(UUID bookingId, String bookingCode, long amount, String method, Instant expireAt, String clientIp) {
        if (!BANK_CODE.containsKey(method)) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "METHOD_NOT_SUPPORTED", "Phương thức thanh toán không hỗ trợ");
        }
        Instant now = clock.instant();
        if (!expireAt.isAfter(now)) {
            throw new PaymentException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Đơn đã hết thời gian giữ vé");
        }
        PaymentRepository.Row p = tx.execute(s -> {
            payments.lockBooking(bookingId);
            if (payments.byStatus(bookingId, "SUCCEEDED").isPresent() || payments.byStatus(bookingId, "REFUNDED").isPresent()) {
                throw new PaymentException(HttpStatus.CONFLICT, "ALREADY_PAID", "Đơn hàng đã được thanh toán");
            }
            var pending = payments.byStatus(bookingId, "PENDING");
            if (pending.isPresent()) {
                var cur = pending.get();
                if (cur.amount() == amount && cur.method().equals(method) && cur.expireAt().isAfter(now)) {
                    return cur;                                   // bấm lại → cùng giao dịch
                }
                payments.failPending(cur.id(), "REPLACED");       // đổi phương thức / hết hạn → bỏ attempt cũ
            }
            int attempt = payments.latest(bookingId).map(r -> r.attempt() + 1).orElse(1);
            var row = new PaymentRepository.Row(UUID.randomUUID(), bookingId, bookingCode, attempt, bookingCode + "-" + attempt,
                    amount, method, "PENDING", expireAt, VNP_TIME.format(now), now);
            payments.insert(row);
            return row;
        });
        return new Created(p.id(), payUrl + "?" + signer.signedQuery(payParams(p, clientIp)), p.expireAt());
    }

    private Map<String, String> payParams(PaymentRepository.Row p, String clientIp) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("vnp_Version", "2.1.0");
        m.put("vnp_Command", "pay");
        m.put("vnp_TmnCode", tmnCode);
        m.put("vnp_Amount", Long.toString(p.amount() * 100));        // VNPay: số tiền × 100
        m.put("vnp_CurrCode", "VND");
        m.put("vnp_TxnRef", p.txnRef());
        m.put("vnp_OrderInfo", "Thanh toan don " + p.bookingCode());
        m.put("vnp_OrderType", "other");
        m.put("vnp_Locale", "vn");
        m.put("vnp_BankCode", BANK_CODE.get(p.method()));
        m.put("vnp_ReturnUrl", publicBaseUrl + "/api/payments/vnpay/return");
        m.put("vnp_IpAddr", clientIp == null ? "127.0.0.1" : clientIp);
        m.put("vnp_CreateDate", p.vnpCreateDate());   // cố định → bấm lại ra đúng URL cũ
        m.put("vnp_ExpireDate", VNP_TIME.format(p.expireAt()));
        return m;
    }

    /**
     * IPN – mã trả về theo đặc tả VNPay: 00 ok · 01 không thấy đơn · 02 đã xử lý · 04 sai số tiền · 97 sai chữ ký · 99 lỗi khác.
     * Đổi trạng thái + ghi Outbox trong 1 transaction.
     */
    public IpnResponse handleIpn(Map<String, String> params) {
        if (!signer.verify(params)) {
            log.warn("IPN sai chữ ký: txnRef={}", params.get("vnp_TxnRef"));
            return new IpnResponse("97", "Invalid Checksum");
        }
        var found = payments.byTxnRef(params.getOrDefault("vnp_TxnRef", ""));
        if (found.isEmpty()) {
            return new IpnResponse("01", "Order not found");
        }
        var p = found.get();
        long amount;
        try {
            amount = Long.parseLong(params.getOrDefault("vnp_Amount", "-1")) / 100;
        } catch (NumberFormatException e) {
            amount = -1;
        }
        if (amount != p.amount()) {
            log.error("IPN sai số tiền: txnRef={} cổng={} đơn={}", p.txnRef(), amount, p.amount());
            return new IpnResponse("04", "Invalid amount");
        }
        String rc = params.getOrDefault("vnp_ResponseCode", "");
        boolean ok = "00".equals(rc) && "00".equals(params.getOrDefault("vnp_TransactionStatus", ""));
        if (!settle(p, ok, params.get("vnp_TransactionNo"), rc, "IPN")) {
            return new IpnResponse("02", "Order already confirmed");
        }
        return new IpnResponse("00", "Confirm Success");
    }

    /**
     * Áp kết quả cuối của 1 giao dịch – DÙNG CHUNG cho IPN và job đối soát: PENDING → SUCCEEDED/FAILED + Outbox
     * trong 1 transaction. false = đã có kết quả từ trước (IPN và đối soát chạy đua → chỉ 1 bên thắng).
     */
    boolean settle(PaymentRepository.Row p, boolean ok, String txnNo, String rc, String source) {
        String status = ok ? "SUCCEEDED" : "FAILED";
        Boolean changed = tx.execute(s -> {
            // Thành công tới SAU khi đối soát đã kết luận "khách bỏ ngang / chưa trả" (IPN cực trễ, trả sau hạn) → tiền đã
            // bị trừ: mở lại FAILED(ABANDONED|NOT_PAID) → SUCCEEDED để booking quyết toán (còn vé → chốt, hết → hoàn tiền).
            if (!payments.complete(p.id(), status, txnNo, rc) && !(ok && payments.reopenAbandoned(p.id(), txnNo, rc))) {
                return false;
            }
            outbox.append(PaymentEvents.TOPIC, p.bookingId(), ok ? PaymentEvents.SUCCEEDED : PaymentEvents.FAILED,
                    new PaymentEvents.PaymentResult(p.id(), p.bookingId(), status, p.amount(), p.method(), txnNo, rc, clock.instant()));
            return true;
        });
        if (Boolean.TRUE.equals(changed)) {
            log.info("{}: {} {} {} đ → {} (rc={})", source, p.bookingCode(), p.txnRef(), p.amount(), status, rc);
        }
        return Boolean.TRUE.equals(changed);
    }

    /** Ứng viên đối soát (PENDING quá lâu). */
    public java.util.List<PaymentRepository.Row> pendingOlderThan(Instant olderThan, int limit) {
        return payments.pendingOlderThan(olderThan, limit);
    }

    /** Job đối soát áp kết quả querydr – cùng đường với IPN. */
    public boolean settleFromReconcile(PaymentRepository.Row p, boolean ok, String txnNo, String rc) {
        return settle(p, ok, txnNo, rc, "ĐỐI SOÁT");
    }

    /** Return URL: chỉ verify chữ ký rồi đưa về UI – KHÔNG đổi trạng thái (IPN là nguồn sự thật). */
    public String returnRedirect(Map<String, String> params) {
        if (!signer.verify(params)) {
            return uiBaseUrl + "/?payment=invalid";
        }
        return payments.byTxnRef(params.getOrDefault("vnp_TxnRef", ""))
                .map(p -> uiBaseUrl + "/orders/" + p.bookingId())
                .orElse(uiBaseUrl + "/?payment=unknown");
    }

    /** Hoàn tiền (cổng giả lập: hoàn ngay). Idempotent: đã hoàn → coi như xong. */
    public boolean refund(UUID bookingId) {
        Boolean done = tx.execute(s -> {
            payments.lockBooking(bookingId);
            if (payments.byStatus(bookingId, "REFUNDED").isPresent()) {
                return true;
            }
            var paid = payments.byStatus(bookingId, "SUCCEEDED")
                    .orElseThrow(() -> new PaymentException(HttpStatus.CONFLICT, "NOT_PAID", "Đơn chưa thanh toán"));
            payments.refund(paid.id());
            outbox.append(PaymentEvents.TOPIC, bookingId, PaymentEvents.REFUNDED,
                    new PaymentEvents.PaymentResult(paid.id(), bookingId, "REFUNDED", paid.amount(), paid.method(), null, null, clock.instant()));
            log.info("Hoàn tiền {} {} đ", paid.bookingCode(), paid.amount());
            return true;
        });
        return Boolean.TRUE.equals(done);
    }
}
