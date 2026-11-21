package com.unbutu.ticket.booking.pricing;

import com.unbutu.ticket.events.catalog.CatalogEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Dựng bảng giá + thông tin sự kiện từ catalog (consumer group riêng booking-service). Idempotent nhờ version. */
@Slf4j
@Component
@RequiredArgsConstructor
class CatalogEventsListener {

    private final JsonMapper json;
    private final PriceBook book;

    @KafkaListener(topics = CatalogEvents.TOPIC)
    void onMessage(String value) {
        JsonNode envelope = json.readTree(value);
        JsonNode payload = envelope.path("payload");
        switch (envelope.path("type").asString()) {
            case CatalogEvents.TICKET_TYPE_UPSERTED -> {
                var e = json.treeToValue(payload, CatalogEvents.TicketTypeUpserted.class);
                book.upsertPrice(e.ticketTypeId(), e.eventId(), e.name(), e.price(), e.status(), e.updatedAt());
                log.info("catalog → bảng giá: {} {} {} đ {}", e.eventId(), e.name(), e.price(), e.status());
            }
            case CatalogEvents.TICKET_TYPE_DELETED ->
                    book.markDeleted(json.treeToValue(payload, CatalogEvents.TicketTypeDeleted.class).ticketTypeId());
            case CatalogEvents.EVENT_UPSERTED -> {
                var e = json.treeToValue(payload, CatalogEvents.EventUpserted.class);
                book.upsertEvent(e.eventId(), e.title(), e.venue(), e.startsAt(), e.updatedAt());
            }
            default -> {
                // event.deleted: đơn cũ vẫn cần hiển thị tên sự kiện → giữ lại; type lạ → bỏ qua (tương thích tiến).
            }
        }
    }
}
