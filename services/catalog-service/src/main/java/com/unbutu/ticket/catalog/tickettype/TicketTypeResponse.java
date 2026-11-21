package com.unbutu.ticket.catalog.tickettype;

import java.time.Instant;
import java.util.UUID;

/** Bản đầy đủ cho admin. {@code deletable=false}: đã từng mở bán → UI đề nghị tạm dừng thay vì xoá. */
public record TicketTypeResponse(UUID id, String code, UUID eventId, String eventTitle, String name, long price,
                                 int totalQuantity, TicketType.Status status, boolean deletable, Instant updatedAt) {

    static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(t.getId(), t.getCode(), t.getEvent().getId(), t.getEvent().getTitle(),
                t.getName(), t.getPrice(), t.getTotalQuantity(), t.getStatus(), !t.everOnSale(), t.getUpdatedAt());
    }

    /** Bản công khai cho người mua (A8). */
    public record Public(UUID id, String name, long price, int totalQuantity, TicketType.Status status) {

        static Public from(TicketType t) {
            return new Public(t.getId(), t.getName(), t.getPrice(), t.getTotalQuantity(), t.getStatus());
        }
    }
}
