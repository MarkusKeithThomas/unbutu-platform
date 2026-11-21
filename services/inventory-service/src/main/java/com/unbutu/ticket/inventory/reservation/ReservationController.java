package com.unbutu.ticket.inventory.reservation;

import com.unbutu.ticket.inventory.stock.StockStore;
import com.unbutu.ticket.inventory.web.InventoryException;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API nội bộ (gateway KHÔNG route /internal/**): chỉ service trong network gọi được. */
@RestController
@RequestMapping("/internal/events/{eventId}/reservations")
@RequiredArgsConstructor
class ReservationController {

    record ReservationResponse(UUID reservationId, String status, Instant expiresAt) {
    }

    private final StockStore store;
    private final Clock clock;

    @Value("${inventory.hold.ttl:10m}")
    private Duration maxTtl;

    @PostMapping
    ReservationResponse reserve(@PathVariable UUID eventId, @Valid @RequestBody ReservationRequest req) {
        Map<UUID, Integer> items = new LinkedHashMap<>();
        req.items().forEach(i -> items.merge(i.ticketTypeId(), i.quantity(), Integer::sum));
        long ttl = Math.min(req.ttlSeconds() == null ? maxTtl.toSeconds() : req.ttlSeconds(), maxTtl.toSeconds());
        var r = store.reserve(eventId, req.reservationId(), items, clock.instant().plusSeconds(ttl), ttl);
        return switch (r.outcome()) {
            case RESERVED -> new ReservationResponse(req.reservationId(), "RESERVED", r.expiresAt());
            case SOLD_OUT -> throw InventoryException.conflict("SOLD_OUT", "Hạng vé đã hết hoặc không đủ số lượng", r.ticketTypeId());
            case NOT_ON_SALE -> throw InventoryException.conflict("NOT_ON_SALE", "Hạng vé không mở bán", r.ticketTypeId());
        };
    }

    /** Huỷ sớm ("← Chọn lại vé") hoặc bù trừ khi booking lỗi. Idempotent. */
    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable UUID eventId, @PathVariable UUID reservationId) {
        store.release(eventId, reservationId);
    }
}
