package com.unbutu.ticket.booking.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Gọi inventory /internal/** (RestClient.Builder inject → propagate trace + X-Request-Id). Timeout ngắn:
 * inventory chậm thì báo lỗi nhanh cho khách, không giữ thread (đường lên tải cao).
 */
@Slf4j
@Component
public class InventoryClient {

    public enum Outcome { RESERVED, SOLD_OUT, NOT_ON_SALE }

    public record ReserveResult(Outcome outcome, UUID ticketTypeId, Instant expiresAt) {
    }

    /** Inventory không trả lời / lỗi 5xx → booking huỷ đơn, khách thử lại. */
    public static class UnavailableException extends RuntimeException {
        UnavailableException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private final RestClient http;

    InventoryClient(RestClient.Builder builder, @Value("${booking.inventory-url}") String baseUrl) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(500));
        rf.setReadTimeout(Duration.ofSeconds(2));
        this.http = builder.baseUrl(baseUrl).requestFactory(rf).build();
    }

    public ReserveResult reserve(UUID eventId, UUID reservationId, Map<UUID, Integer> items, long ttlSeconds) {
        var body = Map.of("reservationId", reservationId, "ttlSeconds", ttlSeconds,
                "items", items.entrySet().stream().map(e -> Map.of("ticketTypeId", e.getKey(), "quantity", e.getValue())).toList());
        try {
            return http.post().uri("/internal/events/{e}/reservations", eventId).body(body)
                    .exchange((req, res) -> {
                        HttpStatusCode s = res.getStatusCode();
                        JsonNode json = res.bodyTo(JsonNode.class);
                        if (s.is2xxSuccessful()) {
                            return new ReserveResult(Outcome.RESERVED, null, Instant.parse(json.path("expiresAt").asString()));
                        }
                        if (s.value() == 409) {
                            String code = json.path("code").asString();
                            UUID tt = json.hasNonNull("ticketTypeId") ? UUID.fromString(json.path("ticketTypeId").asString()) : null;
                            return new ReserveResult("SOLD_OUT".equals(code) ? Outcome.SOLD_OUT : Outcome.NOT_ON_SALE, tt, null);
                        }
                        throw new UnavailableException("inventory trả HTTP " + s.value(), null);
                    });
        } catch (RestClientException e) {
            throw new UnavailableException("không gọi được inventory: " + e.getMessage(), e);
        }
    }

    /** Best-effort: lỗi thì thôi – hold có hạn, sweeper của inventory tự trả vé. Idempotent phía inventory. */
    public void release(UUID eventId, UUID reservationId) {
        try {
            http.delete().uri("/internal/events/{e}/reservations/{r}", eventId, reservationId).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Nhả vé {} lỗi (inventory sweeper sẽ tự trả khi hết hạn): {}", reservationId, e.toString());
        }
    }

}
