package com.unbutu.ticket.booking.booking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Cửa công khai cho các package khác (quyết toán thanh toán) – repository giữ package-private. */
@Component
@RequiredArgsConstructor
public class BookingQueries {

    public record Snapshot(UUID id, String code, UUID eventId, String status, long total, int ticketCount, Map<UUID, Integer> items) {
    }

    private final BookingRepository repo;

    public Optional<Snapshot> snapshot(UUID id) {
        return repo.findById(id).map(r -> {
            Map<UUID, Integer> items = new LinkedHashMap<>();
            repo.items(List.of(id)).getOrDefault(id, List.of()).forEach(i -> items.put(i.ticketTypeId(), i.quantity()));
            return new Snapshot(r.id(), r.code(), r.eventId(), r.status(), r.total(), r.ticketCount(), items);
        });
    }

    public void backToPending(UUID id) {
        repo.backToPending(id);
    }

    public boolean confirm(UUID id) {
        return repo.confirm(id);
    }

    public void refundPending(UUID id, String reason) {
        repo.refundPending(id, reason);
    }

    public void refunded(UUID id) {
        repo.refunded(id);
    }
}
