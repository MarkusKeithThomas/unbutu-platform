package com.unbutu.ticket.booking.payment;

import com.unbutu.ticket.booking.web.BookingException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Gọi payment-service /internal/** (RestClient.Builder inject → propagate trace). */
@Component
public class PaymentClient {

    private final RestClient http;

    PaymentClient(RestClient.Builder builder, @Value("${booking.payment-url}") String baseUrl) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(500));
        rf.setReadTimeout(Duration.ofSeconds(3));
        this.http = builder.baseUrl(baseUrl).requestFactory(rf).build();
    }

    /** → paymentUrl. Lỗi nghiệp vụ của payment (ALREADY_PAID, HOLD_EXPIRED…) chuyển nguyên code cho UI. */
    public String createPayment(UUID bookingId, String code, long amount, String method, Instant expireAt, String clientIp) {
        Map<String, Object> body = new HashMap<>();
        body.put("bookingId", bookingId);
        body.put("bookingCode", code);
        body.put("amount", amount);
        body.put("method", method);
        body.put("expireAt", expireAt);
        body.put("clientIp", clientIp);
        try {
            return http.post().uri("/internal/payments").body(body).exchange((req, res) -> {
                JsonNode json = res.bodyTo(JsonNode.class);
                if (res.getStatusCode().is2xxSuccessful()) {
                    return json.path("paymentUrl").asString();
                }
                if (res.getStatusCode().is4xxClientError() && json != null && json.hasNonNull("code")) {
                    throw new BookingException(HttpStatus.valueOf(res.getStatusCode().value()), json.path("code").asString(),
                            json.path("detail").asString());
                }
                throw unavailable(null);
            });
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    /** Hoàn tiền (idempotent phía payment). Lỗi → ném ra để consumer retry. */
    public void refund(UUID bookingId) {
        try {
            http.post().uri("/internal/payments/bookings/{b}/refund", bookingId).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new IllegalStateException("hoàn tiền lỗi, sẽ thử lại: " + e.getMessage(), e);
        }
    }

    private static BookingException unavailable(Throwable cause) {
        var e = new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_UNAVAILABLE", "Cổng thanh toán đang bận, vui lòng thử lại");
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }
}
