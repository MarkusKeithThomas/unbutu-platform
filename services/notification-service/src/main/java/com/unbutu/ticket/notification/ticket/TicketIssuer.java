package com.unbutu.ticket.notification.ticket;

import com.unbutu.ticket.events.booking.BookingEvents;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * booking.confirmed → phát N vé (N = tổng số lượng các hạng), mỗi vé 1 QR ký ES256. Idempotent: id vé = UUID theo tên
 * (bookingId + seq), UNIQUE (booking_id, seq) + ON CONFLICT DO NOTHING → event lặp / replay không sinh vé thứ 2.
 * 1 transaction cho cả đơn: không bao giờ có đơn chỉ được phát một nửa số vé.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TicketIssuer {

    private final JsonMapper json;
    private final TicketRepository tickets;
    private final QrSigner qr;
    private final Clock clock;

    // @Transactional ở đây (không ở issue): listener được gọi qua proxy; gọi issue() nội bộ sẽ bỏ qua proxy → mất transaction.
    @Transactional
    // concurrency: N luồng chia 6 partition (key = bookingId → mọi event của 1 đơn vẫn tuần tự). 1 luồng ≈ 10 đơn/s trên prod
    // (load test 2026-11-22: docs/loadtest/2026-11-22-purchase-prod.md). Spring tự thêm "-0..N-1" vào group.instance.id.
    @KafkaListener(topics = BookingEvents.TOPIC, concurrency = "${notification.kafka.booking-concurrency}",
            properties = "group.instance.id=${notification.kafka.instance-id}-booking")
    void onMessage(String value) {
        JsonNode envelope = json.readTree(value);
        if (BookingEvents.CONFIRMED.equals(envelope.path("type").asString())) {
            issue(json.treeToValue(envelope.path("payload"), BookingEvents.BookingConfirmed.class));
        }
        // type khác (sau này: booking.refunded → huỷ vé) → bỏ qua (tương thích tiến).
    }

    private void issue(BookingEvents.BookingConfirmed b) {
        var now = clock.instant();
        int seq = 0;
        int created = 0;
        for (BookingEvents.Item item : b.items()) {
            for (int i = 0; i < item.quantity(); i++) {
                seq++;
                UUID id = UUID.nameUUIDFromBytes((b.bookingId() + ":" + seq).getBytes(StandardCharsets.UTF_8));
                var row = new TicketRepository.Row(id, b.bookingId(), b.code(), b.userId(), seq, b.eventId(), b.eventTitle(),
                        b.venue(), b.startsAt(), item.ticketTypeId(), item.name(), item.unitPrice(), "VALID",
                        qr.sign(id, b.eventId(), item.ticketTypeId(), now), now);
                if (tickets.insert(row)) {
                    created++;
                }
            }
        }
        if (created > 0) {
            log.info("Đơn {}: phát {} vé", b.code(), created);
        } else {
            log.info("Đơn {}: vé đã phát trước đó (event lặp) – bỏ qua", b.code());
        }
    }
}
