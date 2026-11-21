package com.unbutu.ticket.payment.mock;

import com.unbutu.ticket.payment.PaymentService;
import com.unbutu.ticket.payment.vnpay.VnpaySigner;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/**
 * CỔNG THANH TOÁN GIẢ LẬP – nói đúng giao thức VNPay 2.1.0 (URL ký HMAC-SHA512 → trang ngân hàng → IPN server→server
 * → return URL). Không trừ tiền thật. Dùng để chạy end-to-end và LUYỆN SỰ CỐ: IPN chậm (ipn-delay), IPN mất
 * (ipn-drop-rate), trả sau hạn giữ (allow-late). Thay bằng VNPay sandbox: đổi pay-url / tmn-code / hash-secret.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/mock/pay")
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
class MockGatewayController {

    private final VnpaySigner signer;
    private final RestClient http;
    private final Clock clock;
    private final String ipnUrl;
    private final Duration ipnDelay;
    private final double ipnDropRate;
    private final boolean allowLate;

    MockGatewayController(VnpaySigner signer, RestClient.Builder builder, Clock clock,
                          @Value("${payment.mock.ipn-url}") String ipnUrl, @Value("${payment.mock.ipn-delay}") Duration ipnDelay,
                          @Value("${payment.mock.ipn-drop-rate}") double ipnDropRate, @Value("${payment.mock.allow-late}") boolean allowLate) {
        this.signer = signer;
        this.http = builder.build();
        this.clock = clock;
        this.ipnUrl = ipnUrl;
        this.ipnDelay = ipnDelay;
        this.ipnDropRate = ipnDropRate;
        this.allowLate = allowLate;
    }

    /** Trang "ngân hàng": kiểm chữ ký merchant như VNPay, hiện số tiền + nút kết quả. */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> page(@RequestParam Map<String, String> params) {
        if (!signer.verify(params)) {
            return html(HttpStatus.BAD_REQUEST, "Sai chữ ký", "<p>Yêu cầu thanh toán không hợp lệ (sai chữ ký merchant).</p>");
        }
        boolean expired = isExpired(params);
        long amount = Long.parseLong(params.get("vnp_Amount")) / 100;
        String q = HtmlUtils.htmlEscape(VnpaySigner.canonical(params) + "&" + VnpaySigner.HASH + "=" + params.get(VnpaySigner.HASH));
        StringBuilder body = new StringBuilder()
                .append("<p class=\"warn\">CỔNG GIẢ LẬP – không trừ tiền thật</p>")
                .append("<dl><dt>Đơn hàng</dt><dd>").append(esc(params.get("vnp_OrderInfo"))).append("</dd>")
                .append("<dt>Mã giao dịch merchant</dt><dd>").append(esc(params.get("vnp_TxnRef"))).append("</dd>")
                .append("<dt>Phương thức</dt><dd>").append(esc(params.get("vnp_BankCode"))).append("</dd>")
                .append("<dt>Số tiền</dt><dd class=\"amt\">").append(String.format("%,d", amount).replace(',', '.')).append(" đ</dd></dl>")
                .append("<form method=\"post\"><input type=\"hidden\" name=\"q\" value=\"").append(q).append("\">");
        if (expired) {
            body.append("<p class=\"err\">Giao dịch đã quá hạn (vnp_ExpireDate).</p>");
            if (allowLate) {
                body.append("<button name=\"action\" value=\"success\" class=\"ok\">Vẫn thanh toán (mô phỏng IPN đến trễ)</button>");
            }
            body.append("<button name=\"action\" value=\"cancel\">Quay lại cửa hàng</button>");
        } else {
            body.append("<button name=\"action\" value=\"success\" class=\"ok\">Thanh toán thành công</button>")
                .append("<button name=\"action\" value=\"fail\">Thanh toán thất bại (không đủ số dư)</button>")
                .append("<button name=\"action\" value=\"cancel\">Huỷ giao dịch</button>");
        }
        body.append("</form>");
        return html(HttpStatus.OK, "Ngân hàng giả lập", body.toString());
    }

    /** Khách bấm kết quả → gửi IPN (server→server) rồi đưa trình duyệt về return URL – đúng thứ tự VNPay. */
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> pay(@RequestParam("q") String q, @RequestParam("action") String action) {
        Map<String, String> req = parse(q);
        if (!signer.verify(req)) {
            return html(HttpStatus.BAD_REQUEST, "Sai chữ ký", "<p>Yêu cầu không hợp lệ.</p>");
        }
        if (isExpired(req) && !(allowLate && "success".equals(action))) {
            action = "cancel";
        }
        String rc = switch (action) {
            case "success" -> "00";
            case "fail" -> "51";      // VNPay: tài khoản không đủ số dư
            default -> "24";          // VNPay: khách huỷ giao dịch
        };
        Map<String, String> res = new LinkedHashMap<>();
        res.put("vnp_TmnCode", req.get("vnp_TmnCode"));
        res.put("vnp_Amount", req.get("vnp_Amount"));
        res.put("vnp_BankCode", req.get("vnp_BankCode"));
        res.put("vnp_CardType", "ATM");
        res.put("vnp_OrderInfo", req.get("vnp_OrderInfo"));
        res.put("vnp_PayDate", PaymentService.VNP_TIME.format(clock.instant()));
        res.put("vnp_ResponseCode", rc);
        res.put("vnp_TransactionNo", Long.toString(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L)));
        res.put("vnp_TransactionStatus", "00".equals(rc) ? "00" : "02");
        res.put("vnp_TxnRef", req.get("vnp_TxnRef"));
        String signed = signer.signedQuery(res);

        sendIpn(signed);   // VNPay gửi IPN cho cả thành công, thất bại lẫn khách huỷ
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(req.get("vnp_ReturnUrl") + "?" + signed)).build();
    }

    private void sendIpn(String signedQuery) {
        if (ThreadLocalRandom.current().nextDouble() < ipnDropRate) {
            log.warn("GIẢ LẬP: bỏ IPN (ipn-drop-rate={}) – cần đối soát để phát hiện", ipnDropRate);
            return;
        }
        Runnable call = () -> {
            try {
                String r = http.get().uri(URI.create(ipnUrl + "?" + signedQuery)).retrieve().body(String.class);
                log.info("GIẢ LẬP: IPN → {}", r);
            } catch (Exception e) {
                log.warn("GIẢ LẬP: gửi IPN lỗi: {}", e.toString());   // VNPay thật sẽ gửi lại theo lịch; giả lập: đối soát (PAY-1b)
            }
        };
        if (ipnDelay.isZero()) {
            call.run();   // IPN tới TRƯỚC khi trình duyệt về → UI thấy kết quả ngay
        } else {
            CompletableFuture.runAsync(call, CompletableFuture.delayedExecutor(ipnDelay.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    private boolean isExpired(Map<String, String> p) {
        String exp = p.get("vnp_ExpireDate");
        if (exp == null) {
            return false;
        }
        Instant e = LocalDateTime.parse(exp, PaymentService.VNP_TIME).atZone(PaymentService.VNP_TIME.getZone()).toInstant();
        return clock.instant().isAfter(e);
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
                .err{color:#B91C1C;font-weight:600}dl{display:grid;grid-template-columns:auto 1fr;gap:8px 16px;font-size:14px}dt{color:#4B5563}dd{margin:0;font-weight:600;overflow-wrap:anywhere}
                .amt{font-size:22px;color:#0B3D91}form{display:flex;flex-direction:column;gap:10px;margin-top:18px}
                button{height:46px;border-radius:10px;border:1px solid #D1D5DB;background:#fff;font:inherit;font-weight:600;cursor:pointer}
                button.ok{background:#0B3D91;color:#fff;border:0}
                </style></head><body><main><h1>%s</h1>%s</main></body></html>""".formatted(esc(title), esc(title), body);
        return ResponseEntity.status(status).contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header("Cache-Control", "no-store").body(page);
    }
}
