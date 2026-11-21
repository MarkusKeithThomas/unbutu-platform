package com.unbutu.ticket.payment.vnpay;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Merchant gọi querydr (merchant_webapi 2.1.0) – cùng code cho cổng giả lập và VNPay sandbox (đổi api-url). */
@Component
public class QueryDrClient {

    private static final DateTimeFormatter VNP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /** responseCode: 00 thấy · 91 không thấy · 97 sai chữ ký · 99 lỗi. transactionStatus: 00 / 01 chưa xong / 02 lỗi. */
    public record Result(String responseCode, String transactionStatus, long amount, String transactionNo,
                         String transactionResponseCode, boolean signatureValid) {
    }

    private final RestClient http;
    private final VnpaySigner signer;
    private final Clock clock;
    private final String apiUrl;
    private final String tmnCode;

    QueryDrClient(RestClient.Builder builder, VnpaySigner signer, Clock clock,
                  @Value("${payment.vnpay.api-url}") String apiUrl, @Value("${payment.vnpay.tmn-code}") String tmnCode) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(2));
        rf.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(rf).build();
        this.signer = signer;
        this.clock = clock;
        this.apiUrl = apiUrl;
        this.tmnCode = tmnCode;
    }

    public Result query(String txnRef, String transactionDate, String orderInfo) {
        Map<String, String> req = new LinkedHashMap<>();
        req.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
        req.put("vnp_Version", "2.1.0");
        req.put("vnp_Command", "querydr");
        req.put("vnp_TmnCode", tmnCode);
        req.put("vnp_TxnRef", txnRef);
        req.put("vnp_OrderInfo", orderInfo);
        req.put("vnp_TransactionDate", transactionDate);
        req.put("vnp_CreateDate", VNP.format(clock.instant()));
        req.put("vnp_IpAddr", "127.0.0.1");
        req.put("vnp_SecureHash", signer.signPipe(req.get("vnp_RequestId"), "2.1.0", "querydr", tmnCode, txnRef, transactionDate,
                req.get("vnp_CreateDate"), "127.0.0.1", orderInfo));
        Map<String, String> r = http.post().uri(apiUrl).body(req).retrieve().body(new ParameterizedTypeReference<>() {
        });
        if (r == null) {
            return new Result("99", "", 0, null, null, false);
        }
        boolean valid = signer.verifyPipe(r.get("vnp_SecureHash"), r.get("vnp_ResponseId"), r.get("vnp_Command"), r.get("vnp_ResponseCode"),
                r.get("vnp_Message"), r.get("vnp_TmnCode"), r.get("vnp_TxnRef"), r.get("vnp_Amount"), r.get("vnp_BankCode"),
                r.get("vnp_PayDate"), r.get("vnp_TransactionNo"), r.get("vnp_TransactionType"), r.get("vnp_TransactionStatus"),
                r.get("vnp_OrderInfo"), r.get("vnp_PromotionCode"), r.get("vnp_PromotionAmount"));
        long amount = r.getOrDefault("vnp_Amount", "").isEmpty() ? 0 : Long.parseLong(r.get("vnp_Amount")) / 100;
        String no = r.getOrDefault("vnp_TransactionNo", "");
        return new Result(r.getOrDefault("vnp_ResponseCode", "99"), r.getOrDefault("vnp_TransactionStatus", ""), amount,
                no.isEmpty() ? null : no, r.get("vnp_TransactionResponseCode"), valid);
    }
}
