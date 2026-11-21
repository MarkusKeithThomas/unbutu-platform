package com.unbutu.ticket.payment.mock;

import com.unbutu.ticket.payment.PaymentService;
import com.unbutu.ticket.payment.mock.fault.Dice;
import com.unbutu.ticket.payment.mock.fault.IpnFault;
import com.unbutu.ticket.payment.mock.fault.IpnPlan;
import com.unbutu.ticket.payment.vnpay.VnpaySigner;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Phía "ngân hàng": lên kế hoạch IPN (happy case + các {@link IpnFault} đang bật), ghi hàng đợi bền, rồi gửi
 * server→server. Merchant trả khác 00/02 hoặc lỗi mạng/timeout → gửi lại theo lịch backoff (như VNPay); hết lịch → GAVE_UP
 * (đối soát của merchant phải tự phát hiện).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
class MockIpnSender {

    private final MockBank bank;
    private final VnpaySigner signer;
    private final List<IpnFault> faults;
    private final Dice dice;
    private final RestClient http;
    private final Clock clock;
    private final String ipnUrl;
    private final String tmnCode;
    private final List<Duration> backoff;

    MockIpnSender(MockBank bank, VnpaySigner signer, List<IpnFault> faults, Dice dice, RestClient.Builder builder, Clock clock,
                  @Value("${payment.mock.ipn-url}") String ipnUrl, @Value("${payment.vnpay.tmn-code}") String tmnCode,
                  @Value("${payment.mock.ipn-retry-backoff}") String backoff) {
        this.bank = bank;
        this.signer = signer;
        this.faults = faults.stream().sorted(Comparator.comparingInt(IpnFault::order)).toList();
        this.dice = dice;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(1));
        rf.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(rf).build();
        this.clock = clock;
        this.ipnUrl = ipnUrl;
        this.tmnCode = tmnCode;
        this.backoff = Arrays.stream(backoff.split(",")).map(String::trim).map(MockIpnSender::parse).toList();
        log.info("Ngân hàng giả lập: lỗi đang bật = {}", this.faults.stream().filter(IpnFault::enabled).map(IpnFault::name).toList());
    }

    /** Danh sách lỗi đang bật (hiện trên trang ngân hàng cho người luyện). */
    List<String> activeFaults() {
        return faults.stream().filter(IpnFault::enabled).map(IpnFault::name).toList();
    }

    /** Lên kế hoạch + xếp hàng IPN cho giao dịch vừa có kết quả. Trả các lỗi đã áp (để log / hiện). */
    List<String> plan(String txnRef) {
        var plan = new IpnPlan();
        faults.stream().filter(IpnFault::enabled).forEach(f -> f.shape(plan, dice));
        String note = String.join(",", plan.applied());
        plan.deliveries().forEach(d -> bank.schedule(txnRef, d, note.isEmpty() ? null : note));
        if (!plan.applied().isEmpty()) {
            log.warn("GIẢ LẬP lỗi cho {}: {} ({} lần gửi)", txnRef, plan.applied(), plan.deliveries().size());
        }
        return plan.applied();
    }

    @Scheduled(fixedDelay = 1000)
    void dispatch() {
        for (var d : bank.claimDue(50)) {
            var txn = bank.find(d.txnRef()).orElse(null);
            if (txn == null) {
                bank.gaveUp(d.id(), "không thấy giao dịch");
                continue;
            }
            if (d.fakeNetworkErrors() > 0) {
                retryOrGiveUp(d, "GIẢ LẬP mất mạng", true);
                continue;
            }
            try {
                JsonNode r = http.get().uri(URI.create(ipnUrl + "?" + query(txn, IpnPlan.Tamper.valueOf(d.tamper()))))
                        .retrieve().body(JsonNode.class);
                String code = r == null ? "" : r.path("RspCode").asString();
                if ("00".equals(code) || "02".equals(code)) {
                    bank.delivered(d.id(), code + " " + r.path("Message").asString());
                } else {
                    retryOrGiveUp(d, "merchant RspCode " + code, false);
                }
            } catch (Exception e) {
                retryOrGiveUp(d, e.getClass().getSimpleName() + ": " + e.getMessage(), false);
            }
        }
    }

    private void retryOrGiveUp(MockBank.Delivery d, String why, boolean fake) {
        if (d.attempt() < backoff.size()) {
            long wait = backoff.get(d.attempt()).toSeconds();
            bank.retryLater(d.id(), wait, why, fake);
            log.info("IPN {} lần {} lỗi ({}) → gửi lại sau {}s", d.txnRef(), d.attempt() + 1, why, wait);
        } else {
            bank.gaveUp(d.id(), why);
            log.warn("IPN {} bỏ sau {} lần ({}) – merchant phải đối soát", d.txnRef(), d.attempt() + 1, why);
        }
    }

    /** Tham số IPN = tham số return, ký HMAC; lỗi giả mạo làm hỏng chữ ký / số tiền. */
    String query(MockBank.Txn t, IpnPlan.Tamper tamper) {
        Map<String, String> m = result(t);
        if (tamper == IpnPlan.Tamper.WRONG_AMOUNT) {
            m.put("vnp_Amount", Long.toString((t.amount() + 1000) * 100));      // ký ĐÚNG trên số tiền SAI (lỗi phía ngân hàng)
        }
        String q = signer.signedQuery(m);
        if (tamper == IpnPlan.Tamper.BAD_SIGNATURE) {
            q = q.substring(0, q.length() - 4) + "dead";                       // chữ ký hỏng
        }
        return q;
    }

    Map<String, String> result(MockBank.Txn t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("vnp_TmnCode", tmnCode);
        m.put("vnp_Amount", Long.toString(t.amount() * 100));
        m.put("vnp_BankCode", t.bankCode());
        m.put("vnp_CardType", "ATM");
        m.put("vnp_OrderInfo", t.orderInfo());
        m.put("vnp_PayDate", t.payDate() == null ? PaymentService.VNP_TIME.format(clock.instant()) : t.payDate());
        m.put("vnp_ResponseCode", t.responseCode() == null ? "99" : t.responseCode());
        m.put("vnp_TransactionNo", t.transactionNo());
        m.put("vnp_TransactionStatus", switch (t.status()) {
            case "SUCCESS" -> "00";
            case "PROCESSING", "OPEN" -> "01";
            default -> "02";
        });
        m.put("vnp_TxnRef", t.txnRef());
        return m;
    }

    private static Duration parse(String s) {
        return s.endsWith("ms") ? Duration.ofMillis(Long.parseLong(s.replace("ms", "")))
                : s.endsWith("m") ? Duration.ofMinutes(Long.parseLong(s.replace("m", "")))
                : Duration.ofSeconds(Long.parseLong(s.replace("s", "")));
    }
}
