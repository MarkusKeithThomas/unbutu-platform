package com.unbutu.ticket.inventory.catalogsync;

import com.unbutu.ticket.events.catalog.CatalogEvents;
import com.unbutu.ticket.inventory.stock.StockLedger;
import com.unbutu.ticket.inventory.stock.StockStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dựng tồn kho từ catalog (không gọi sync sang catalog). Mỗi snapshot có version (updatedAt) → áp lại / replay /
 * đến trễ đều an toàn, không cần bảng dedupe. Thứ tự: Postgres (sổ cái) trước, rồi Redis (cũng idempotent).
 * Lỗi tạm (DB/Redis chết) → retry vô hạn có backoff (KafkaConsumerConfig); JSON hỏng → log + bỏ qua.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CatalogEventsListener {

    private final JsonMapper json;
    private final StockLedger ledger;
    private final StockStore store;

    @KafkaListener(topics = CatalogEvents.TOPIC, properties = "group.instance.id=${inventory.kafka.instance-id}-catalog")
    void onMessage(String value) {
        JsonNode envelope = json.readTree(value);
        String type = envelope.path("type").asString();
        JsonNode payload = envelope.path("payload");
        switch (type) {
            case CatalogEvents.TICKET_TYPE_UPSERTED -> {
                var e = json.treeToValue(payload, CatalogEvents.TicketTypeUpserted.class);
                ledger.upsert(e.ticketTypeId(), e.eventId(), e.totalQuantity(), e.status(), e.updatedAt());
                String r = store.upsertType(e.eventId(), e.ticketTypeId(), e.totalQuantity(), e.status(),
                        e.updatedAt().toEpochMilli(), ledger.sold(e.ticketTypeId()));
                log.info("catalog → tồn kho: {} {} total={} status={} ({})", e.eventId(), e.ticketTypeId(),
                        e.totalQuantity(), e.status(), r);
            }
            case CatalogEvents.TICKET_TYPE_DELETED -> {
                var e = json.treeToValue(payload, CatalogEvents.TicketTypeDeleted.class);
                ledger.markDeleted(e.ticketTypeId());
                store.removeType(e.eventId(), e.ticketTypeId());
                log.info("catalog → tồn kho: xoá hạng vé {} của {}", e.ticketTypeId(), e.eventId());
            }
            default -> {
                // event.upserted / event.deleted / type mới chưa biết: inventory không cần → bỏ qua (tương thích tiến).
            }
        }
    }
}
