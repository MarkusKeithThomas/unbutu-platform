package com.unbutu.ticket.payment.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chữ ký kiểu VNPay 2.1.0: sắp xếp tham số vnp_* theo tên, URL-encode (US-ASCII, space = '+'), nối "k=v&…",
 * HMAC-SHA512 bằng hash secret → vnp_SecureHash (hex). Dùng cho cả URL thanh toán, IPN và return.
 */
@Component
public class VnpaySigner {

    public static final String HASH = "vnp_SecureHash";
    public static final String HASH_TYPE = "vnp_SecureHashType";

    private final byte[] secret;

    VnpaySigner(@Value("${payment.vnpay.hash-secret}") String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("payment.vnpay.hash-secret phải ≥ 16 ký tự");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Chuỗi query đã sắp xếp + encode (không gồm chữ ký). */
    public static String canonical(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_") && !e.getKey().equals(HASH) && !e.getKey().equals(HASH_TYPE))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public String sign(Map<String, String> params) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret, "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(params).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    /** query đầy đủ: canonical + &vnp_SecureHash=… */
    public String signedQuery(Map<String, String> params) {
        return canonical(params) + "&" + HASH + "=" + sign(params);
    }

    /** API merchant_webapi (querydr, refund): HMAC-SHA512 của các trường nối bằng "|" theo thứ tự đặc tả. */
    public String signPipe(String... fields) {
        String data = String.join("|", java.util.Arrays.stream(fields).map(f -> f == null ? "" : f).toList());
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret, "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verifyPipe(String given, String... fields) {
        return given != null && MessageDigest.isEqual(given.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                signPipe(fields).getBytes(StandardCharsets.US_ASCII));
    }

    /** So sánh hằng thời gian (không lộ chữ ký qua độ trễ). */
    public boolean verify(Map<String, String> params) {
        String given = params.get(HASH);
        if (given == null) {
            return false;
        }
        return MessageDigest.isEqual(given.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                sign(params).getBytes(StandardCharsets.US_ASCII));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.US_ASCII);
    }
}
