package com.unbutu.ticket.booking.booking;

import com.unbutu.ticket.events.booking.BookingEvents;
import com.unbutu.ticket.outbox.Outbox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cửa công khai cho các package khác (quyết toán thanh toán) – repository giữ package-private. */
@Component
@RequiredArgsConstructor
public class BookingQueries {

    public record Snapshot(UUID id, String code, UUID eventId, String status, long total, int ticketCount, Map<UUID, Integer> items) {
    }

    private final BookingRepository repo;
    private final Outbox outbox;

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

    /**
     * → CONFIRMED + ghi booking.confirmed vào outbox trong CÙNG transaction: commit thì chắc chắn có event, rollback thì
     * cả hai cùng mất. Event lặp (payment.succeeded đến 2 lần) → UPDATE không đổi dòng nào → không phát lần 2.
     */
    @Transactional
    public boolean confirm(UUID id) {
        if (!repo.confirm(id)) {
            return false;
        }
        var r = repo.confirmedRow(id).orElseThrow();
        var items = repo.items(List.of(id)).getOrDefault(id, List.of()).stream()
                .map(i -> new BookingEvents.Item(i.ticketTypeId(), i.name(), i.unitPrice(), i.quantity()))
                .toList();
        outbox.append(BookingEvents.TOPIC, id, BookingEvents.CONFIRMED, new BookingEvents.BookingConfirmed(
                r.id(), r.code(), r.userId(), r.eventId(), r.eventTitle(), r.venue(), r.startsAt(),
                r.buyerName(), r.buyerEmail(), r.buyerPhone(), r.total(), r.paymentMethod(), items, r.confirmedAt()));
        return true;
    }

    public void refundPending(UUID id, String reason) {
        repo.refundPending(id, reason);
    }

    public void refunded(UUID id) {
        repo.refunded(id);
    }
}
