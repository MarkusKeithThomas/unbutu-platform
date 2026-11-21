package com.unbutu.ticket.events.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Topic {@value #TOPIC} – catalog-service phát, key = id sự kiện (event).
 * inventory dựng tồn kho, booking dựng bảng giá từ đây (không gọi sync sang catalog → catalog sập vẫn bán được vé).
 */
public final class CatalogEvents {

    public static final String TOPIC = "catalog.event.v1";

    public static final String EVENT_UPSERTED = "event.upserted";
    public static final String EVENT_DELETED = "event.deleted";
    public static final String TICKET_TYPE_UPSERTED = "ticket-type.upserted";
    public static final String TICKET_TYPE_DELETED = "ticket-type.deleted";

    private CatalogEvents() {
    }

    /** Trạng thái đầy đủ của sự kiện sau khi tạo/sửa (snapshot, không phải diff → consumer chỉ cần giữ bản mới nhất). */
    public record EventUpserted(UUID eventId, String title, String type, String venue, Instant startsAt, Instant updatedAt) {
    }

    public record EventDeleted(UUID eventId) {
    }

    /**
     * Snapshot hạng vé. {@code status}: DRAFT | ON_SALE | PAUSED. {@code updatedAt} tăng dần theo mỗi lần sửa
     * → consumer bỏ qua bản cũ hơn bản đang có (phòng khi replay).
     */
    public record TicketTypeUpserted(UUID ticketTypeId, UUID eventId, String name, long price, int totalQuantity,
                                     String status, Instant updatedAt) {
    }

    public record TicketTypeDeleted(UUID ticketTypeId, UUID eventId) {
    }
}
