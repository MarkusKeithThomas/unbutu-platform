package com.unbutu.ticket.inventory.stock;

import java.util.List;
import java.util.UUID;

/**
 * Key Redis của 1 sự kiện. Hash tag {@code {eventId}} → mọi key của sự kiện cùng 1 slot: Lua chạy được trên
 * Redis Cluster, và tải giờ mở bán của các sự kiện khác nhau rải ra nhiều shard (đường lên 50k rps).
 */
public record StockKeys(UUID eventId) {

    /** Tập sự kiện đang có vé giữ – sweeper chỉ quét các sự kiện này. */
    public static final String EVENTS_WITH_HOLDS = "inv:events-with-holds";

    private String k(String suffix) {
        return "inv:{" + eventId + "}:" + suffix;
    }

    public String avail() { return k("avail"); }      // HASH ticketTypeId → còn lại
    public String total() { return k("total"); }      // HASH ticketTypeId → tổng phát hành
    public String status() { return k("status"); }    // HASH ticketTypeId → DRAFT|ON_SALE|PAUSED
    public String ver() { return k("ver"); }          // HASH ticketTypeId → version snapshot catalog (millis)
    public String ready() { return k("ready"); }      // STRING: đã dựng từ sổ cái
    public String holds() { return k("holds"); }      // ZSET reservationId → hạn (millis)

    public String hold(UUID reservationId) {          // HASH ticketTypeId → số vé giữ
        return k("hold:" + reservationId);
    }

    List<String> typeState() {
        return List.of(avail(), total(), status(), ver(), ready());
    }
}
