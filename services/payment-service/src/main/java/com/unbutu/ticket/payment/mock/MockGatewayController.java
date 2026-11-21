package com.unbutu.ticket.payment.mock;

import com.unbutu.ticket.payment.PaymentService;
import com.unbutu.ticket.payment.mock.fault.Dice;
import com.unbutu.ticket.payment.mock.fault.MockFaults;
import com.unbutu.ticket.payment.vnpay.VnpaySigner;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * CỔNG THANH TOÁN GIẢ LẬP – nói đúng giao thức VNPay 2.1.0 (URL ký HMAC-SHA512 → trang ngân hàng → IPN server→server
 * → return URL → querydr). Không trừ tiền thật. Mặc định happy case; lỗi thường gặp bật bằng env MOCK_FAULT_* (mock/fault).
 * Thay bằng VNPay sandbox: đổi pay-url / api-url / tmn-code / hash-secret, PAYMENT_MOCK_ENABLED=false.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/mock/pay")
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
class MockGatewayController {

    private final VnpaySigner signer;
    private final MockBank bank;
    private final MockIpnSender ipn;
    private final MockFaults faults;
    private final Dice dice;
    private final Clock clock;
    private final boolean allowLate;

    MockGatewayController(VnpaySigner signer, MockBank bank, MockIpnSender ipn, MockFaults faults, Dice dice, Clock clock,
                          @Value("${payment.mock.allow-late}") boolean allowLate) {
        this.signer = signer;
        this.bank = bank;
        this.ipn = ipn;
        this.faults = faults;
        this.dice = dice;
        this.clock = clock;
        this.allowLate = allowLate;
    }

    /** Trang "ngân hàng": kiểm chữ ký merchant như VNPay, ghi nhận giao dịch (OPEN), hiện số tiền + nút kết quả. */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> page(@RequestParam Map<String, String> params) {
        if (!signer.verify(params)) {
            return html(HttpStatus.BAD_REQUEST, "Sai chữ ký", "<p>Yêu cầu thanh toán không hợp lệ (sai chữ ký merchant).</p>");
        }
        long amount = Long.parseLong(params.get("vnp_Amount")) / 100;
        bank.open(params.get("vnp_TxnRef"), amount, params.get("vnp_OrderInfo"), params.get("vnp_BankCode"),
                params.get("vnp_ReturnUrl"), params.get("vnp_CreateDate"), expireAt(params));
        var txn = bank.find(params.get("vnp_TxnRef")).orElseThrow();
        boolean expired = clock.instant().isAfter(txn.expireAt());
        String q = HtmlUtils.htmlEscape(VnpaySigner.canonical(params) + "&" + VnpaySigner.HASH + "=" + params.get(VnpaySigner.HASH));
        var active = ipn.activeFaults();
        StringBuilder b = new StringBuilder()
                .append("<p class=\"warn\">CỔNG GIẢ LẬP – không trừ tiền thật</p>");
        if (!active.isEmpty() || faults.bankPendingRate() > 0) {
            b.append("<p class=\"faults\">Lỗi giả lập đang bật: ").append(esc(String.join(", ", active)))
                    .append(faults.bankPendingRate() > 0 ? (active.isEmpty() ? "" : ", ") + "bank-pending" : "").append("</p>");
        }
        b.append("<dl><dt>Đơn hàng</dt><dd>").append(esc(txn.orderInfo())).append("</dd>")
                .append("<dt>Mã giao dịch merchant</dt><dd>").append(esc(txn.txnRef())).append("</dd>")
                .append("<dt>Phương thức</dt><dd>").append(esc(txn.bankCode())).append("</dd>")
                .append("<dt>Số tiền</dt><dd class=\"amt\">").append(String.format("%,d", amount).replace(',', '.')).append(" đ</dd></dl>")
                .append("<form method=\"post\"><input type=\"hidden\" name=\"q\" value=\"").append(q).append("\">");
        if (!"OPEN".equals(txn.status())) {
            b.append("<p class=\"err\">Giao dịch đã có kết quả (").append(esc(txn.status())).append(").</p>")
                    .append("<button name=\"action\" value=\"back\">Quay lại cửa hàng</button>");
        } else if (expired) {
            b.append("<p class=\"err\">Giao dịch đã quá hạn (vnp_ExpireDate).</p>");
            if (allowLate) {
                b.append("<button name=\"action\" value=\"success\" class=\"ok\">Vẫn thanh toán (mô phỏng IPN đến trễ)</button>");
            }
            b.append("<button name=\"action\" value=\"cancel\">Quay lại cửa hàng</button>");
        } else {
            b.append("<button name=\"action\" value=\"success\" class=\"ok\">Thanh toán thành công</button>")
                    .append("<button name=\"action\" value=\"fail\">Thanh toán thất bại (không đủ số dư)</button>")
                    .append("<button name=\"action\" value=\"cancel\">Huỷ giao dịch</button>");
        }
        b.append("</form>");
        return html(HttpStatus.OK, "Ngân hàng giả lập", b.toString());
    }

    /**
     * Khách bấm kết quả → ngân hàng chốt giao dịch → lên kế hoạch IPN (happy case + lỗi đang bật, hàng đợi bền) → đưa
     * trình duyệt về return URL. Lỗi "bank-pending": ngân hàng xử lý chậm, IPN gửi khi {@link MockBankSettler} xử lý xong.
     */
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> pay(@RequestParam("q") String q, @RequestParam("action") String action) {
        Map<String, String> req = parse(q);
        if (!signer.verify(req)) {
            return html(HttpStatus.BAD_REQUEST, "Sai chữ ký", "<p>Yêu cầu không hợp lệ.</p>");
        }
        var txn = bank.find(req.get("vnp_TxnRef")).orElse(null);
        if (txn == null) {
            return html(HttpStatus.BAD_REQUEST, "Không thấy giao dịch", "<p>Hãy mở lại trang thanh toán.</p>");
        }
        if ("OPEN".equals(txn.status())) {
            boolean expired = clock.instant().isAfter(txn.expireAt());
            if (expired && !(allowLate && "success".equals(action))) {
                action = "cancel";
            }
            String now = PaymentService.VNP_TIME.format(clock.instant());
            String txnNo = Long.toString(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
            boolean pending = "success".equals(action) && dice.roll(faults.bankPendingRate());
            boolean changed = switch (action) {
                case "success" -> pending
                        ? bank.finish(txn.txnRef(), "PROCESSING", null, txnNo, now, clock.instant().plus(faults.bankPendingDuration()))
                        : bank.finish(txn.txnRef(), "SUCCESS", "00", txnNo, now, null);
                case "fail" -> bank.finish(txn.txnRef(), "FAILED", "51", txnNo, now, null);       // không đủ số dư
                default -> bank.finish(txn.txnRef(), "CANCELLED", "24", null, now, null);          // khách huỷ
            };
            if (changed && pending) {
                log.warn("GIẢ LẬP bank-pending: {} đang xử lý {}s", txn.txnRef(), faults.bankPendingDuration().toSeconds());
            } else if (changed) {
                ipn.plan(txn.txnRef());   // VNPay gửi IPN cho cả thành công, thất bại lẫn huỷ
            }
            txn = bank.find(txn.txnRef()).orElseThrow();
        }
        String back = signer.signedQuery(ipn.result(txn));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(txn.returnUrl() + "?" + back)).build();
    }

    private static Instant expireAt(Map<String, String> p) {
        String exp = p.get("vnp_ExpireDate");
        return exp == null ? Instant.MAX
                : LocalDateTime.parse(exp, PaymentService.VNP_TIME).atZone(PaymentService.VNP_TIME.getZone()).toInstant();
    }

    private static Map<String, String> parse(String q) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.US_ASCII),
                        URLDecoder.decode(kv.substring(i + 1), StandardCharsets.US_ASCII));
            }
        }
        return m;
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static ResponseEntity<String> html(HttpStatus status, String title, String body) {
        String page = """
                <!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>
                body{margin:0;font-family:system-ui,sans-serif;background:#0B3D91;color:#111827;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:16px}
                main{background:#fff;border-radius:14px;max-width:440px;width:100%%;padding:28px;box-shadow:0 12px 40px rgba(0,0,0,.25)}
                h1{margin:0 0 12px;font-size:20px}.warn{background:#FEF3C7;color:#92400E;padding:8px 12px;border-radius:8px;font-size:13px;font-weight:600}
                .err{color:#B91C1C;font-weight:600}.faults{background:#FEE2E2;color:#991B1B;padding:8px 12px;border-radius:8px;font-size:13px}dl{display:grid;grid-template-columns:auto 1fr;gap:8px 16px;font-size:14px}dt{color:#4B5563}dd{margin:0;font-weight:600;overflow-wrap:anywhere}
                .amt{font-size:22px;color:#0B3D91}form{display:flex;flex-direction:column;gap:10px;margin-top:18px}
                button{height:46px;border-radius:10px;border:1px solid #D1D5DB;background:#fff;font:inherit;font-weight:600;cursor:pointer}
                button.ok{background:#0B3D91;color:#fff;border:0}
                </style></head><body><main><h1>%s</h1>%s</main></body></html>""".formatted(esc(title), esc(title), body);
        return ResponseEntity.status(status).contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header("Cache-Control", "no-store").body(page);
    }
}
