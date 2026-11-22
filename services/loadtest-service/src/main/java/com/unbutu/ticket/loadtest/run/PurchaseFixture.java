package com.unbutu.ticket.loadtest.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Dữ liệu cho kịch bản mua vé (purchase), chuẩn bị NGAY LÚC BẤM CHẠY bằng access token của ADMIN đang bấm:
 * <ol>
 *   <li>Sự kiện ẨN mới (catalog hidden=true → khách không thấy) + 1 hạng vé ON_SALE kho lớn.</li>
 *   <li>Cờ bỏ giới hạn 4 vé/tài khoản cho ĐÚNG sự kiện đó (booking limit-bypass, có hạn tự tắt).</li>
 *   <li>1 tài khoản test thường (đăng ký nếu chưa có) – k6 dùng nó mua song song nhiều luồng.</li>
 * </ol>
 * Xong lượt: tắt cờ + tạm dừng bán (best-effort – token ADMIN có thể đã hết hạn; cờ vẫn tự hết hạn).
 * Đơn/vé test ở lại trong DB, gắn với sự kiện ẩn (lọc được theo event_id).
 */
@Slf4j
@Component
class PurchaseFixture {

    static final int STOCK = 1_000_000;
    private static final long PRICE = 10_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    record Fixture(UUID eventId, UUID ticketTypeId, Map<String, String> env) {
    }

    private final LoadtestProperties props;
    private final Clock clock;
    private final RestClient catalog;
    private final RestClient booking;
    private final RestClient identity;

    PurchaseFixture(LoadtestProperties props, Clock clock, RestClient.Builder http) {
        this.props = props;
        this.clock = clock;
        this.catalog = http.clone().baseUrl(props.catalogUrl()).build();
        this.booking = http.clone().baseUrl(props.bookingUrl()).build();
        this.identity = http.clone().baseUrl(props.identityUrl()).build();
    }

    Fixture prepare(String adminToken, Duration runFor) {
        var acc = props.account();
        if (acc == null || acc.password() == null || acc.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chưa cấu hình tài khoản test (LOADTEST_ACCOUNT_PASSWORD) – không chạy được kịch bản mua vé");
        }
        String bearer = "Bearer " + adminToken;
        try {
            Instant now = clock.instant();
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("title", "Load Test " + STAMP.format(now));
            ev.put("type", "CONCERT");
            ev.put("description", "Sự kiện load test tạo tự động (ẩn khỏi khách) – không phải sự kiện thật.");
            ev.put("venue", "Load test");
            ev.put("startsAt", now.plus(30, ChronoUnit.DAYS).toString());
            ev.put("hidden", true);
            JsonNode event = catalog.post().uri("/api/catalog/admin/events").header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON).body(ev).retrieve().body(JsonNode.class);
            UUID eventId = UUID.fromString(event.path("id").asString());

            JsonNode tt = catalog.post().uri("/api/catalog/admin/ticket-types").header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON).body(ticketType(eventId, "ON_SALE"))
                    .retrieve().body(JsonNode.class);
            UUID ticketTypeId = UUID.fromString(tt.path("id").asString());

            long minutes = Math.min(120, runFor.toMinutes() + 15);
            booking.put().uri("/api/bookings/admin/limit-bypass/{id}?minutes={m}", eventId, minutes)
                    .header("Authorization", bearer).retrieve().toBodilessEntity();

            ensureAccount(acc.email(), acc.password());
            log.info("Chuẩn bị load test mua vé: sự kiện ẩn {} hạng vé {} (kho {}), bỏ giới hạn {} phút, tài khoản {}",
                    eventId, ticketTypeId, STOCK, minutes, acc.email());
            return new Fixture(eventId, ticketTypeId, Map.of(
                    "EVENT_ID", eventId.toString(), "TICKET_TYPE_ID", ticketTypeId.toString(),
                    "LT_EMAIL", acc.email(), "LT_PASSWORD", acc.password()));
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không chuẩn bị được dữ liệu mua vé: " + e.getMessage());
        }
    }

    /** Tắt cờ + tạm dừng bán. Lỗi chỉ ghi log: cờ tự hết hạn, sự kiện vẫn ẩn. */
    void cleanup(String adminToken, Fixture f) {
        String bearer = "Bearer " + adminToken;
        try {
            booking.delete().uri("/api/bookings/admin/limit-bypass/{id}", f.eventId()).header("Authorization", bearer)
                    .retrieve().toBodilessEntity();
            catalog.put().uri("/api/catalog/admin/ticket-types/{id}", f.ticketTypeId()).header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON).body(ticketType(f.eventId(), "PAUSED"))
                    .retrieve().toBodilessEntity();
            log.info("Dọn load test mua vé: tắt bỏ giới hạn + tạm dừng bán sự kiện {}", f.eventId());
        } catch (RestClientException e) {
            log.warn("Dọn load test mua vé chưa xong (cờ tự hết hạn, sự kiện vẫn ẩn): {}", e.getMessage());
        }
    }

    private static Map<String, Object> ticketType(UUID eventId, String status) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("eventId", eventId);
        t.put("name", "Vé load test");
        t.put("description", "Hạng vé load test");
        t.put("price", PRICE);
        t.put("totalQuantity", STOCK);
        t.put("status", status);
        return t;
    }

    /** Đăng nhập thử; sai/không có → đăng ký (409 = email đã có nhưng sai mật khẩu → báo lỗi rõ). */
    private void ensureAccount(String email, String password) {
        try {
            identity.post().uri("/api/identity/login").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password)).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized e) {
            try {
                identity.post().uri("/api/identity/register").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password, "fullName", "Load Test"))
                        .retrieve().toBodilessEntity();
                log.info("Đã đăng ký tài khoản load test {}", email);
            } catch (HttpClientErrorException.Conflict c) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Tài khoản " + email + " đã tồn tại nhưng sai mật khẩu (LOADTEST_ACCOUNT_PASSWORD)");
            }
        }
    }
}
