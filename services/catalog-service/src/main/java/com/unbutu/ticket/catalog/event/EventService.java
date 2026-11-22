package com.unbutu.ticket.catalog.event;

import com.unbutu.ticket.catalog.cache.CatalogCache;
import com.unbutu.ticket.outbox.Outbox;
import com.unbutu.ticket.catalog.tickettype.TicketTypeRepository;
import com.unbutu.ticket.events.catalog.CatalogEvents;
import com.unbutu.ticket.catalog.web.CatalogException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final Outbox outbox;
    private final CatalogCache cache;
    private final Clock clock;

    public Event get(UUID id) {
        return events.findById(id).orElseThrow(EventService::notFound);
    }

    @Transactional
    public Event create(EventRequest req) {
        Event event = events.save(Event.create(req, clock.instant()));
        publishUpserted(event);
        return event;
    }

    @Transactional
    public Event update(UUID id, EventRequest req) {
        Event event = get(id);
        event.apply(req, clock.instant());
        publishUpserted(event);
        return event;
    }

    /** Còn hạng vé → không xoá (hạng vé có thể đã có đơn); admin xoá/tạm dừng hạng vé trước. */
    @Transactional
    public void delete(UUID id) {
        Event event = get(id);
        if (ticketTypes.existsByEventId(id)) {
            throw CatalogException.conflict("EVENT_HAS_TICKET_TYPES", "Sự kiện còn hạng vé, hãy xoá hạng vé trước");
        }
        events.delete(event);
        cache.invalidateAllAfterCommit();   // admin ghi → xoá cache API công khai sau commit
        outbox.append(CatalogEvents.TOPIC, id, CatalogEvents.EVENT_DELETED, new CatalogEvents.EventDeleted(id));
    }

    private void publishUpserted(Event e) {
        cache.invalidateAllAfterCommit();   // admin ghi → xoá cache API công khai sau commit
        outbox.append(CatalogEvents.TOPIC, e.getId(), CatalogEvents.EVENT_UPSERTED, new CatalogEvents.EventUpserted(
                e.getId(), e.getTitle(), e.getType().name(), e.getVenue(), e.getStartsAt(), e.getUpdatedAt(), e.isHidden()));
    }

    public static CatalogException notFound() {
        return CatalogException.notFound("EVENT_NOT_FOUND", "Không tìm thấy sự kiện");
    }
}
