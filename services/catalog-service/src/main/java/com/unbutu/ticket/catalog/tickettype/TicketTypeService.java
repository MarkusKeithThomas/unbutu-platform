package com.unbutu.ticket.catalog.tickettype;

import com.unbutu.ticket.catalog.event.Event;
import com.unbutu.ticket.catalog.event.EventRepository;
import com.unbutu.ticket.catalog.event.EventService;
import com.unbutu.ticket.outbox.Outbox;
import com.unbutu.ticket.catalog.web.CatalogException;
import com.unbutu.ticket.events.catalog.CatalogEvents;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private final TicketTypeRepository ticketTypes;
    private final EventRepository events;
    private final Outbox outbox;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TicketTypeResponse> listAll() {
        return ticketTypes.findAllWithEvent().stream().map(TicketTypeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketTypeResponse.Public> listVisible(UUID eventId) {
        return ticketTypes.findByEventIdAndStatusInOrderByPriceDesc(eventId, TicketType.VISIBLE)
                .stream().map(TicketTypeResponse.Public::from).toList();
    }

    @Transactional
    public TicketTypeResponse create(TicketTypeRequest req) {
        Event event = event(req.eventId());
        if (ticketTypes.existsByEventIdAndNameIgnoreCase(event.getId(), req.name().strip())) {
            throw nameTaken();
        }
        // saveAndFlush: INSERT ngay để Hibernate đọc lại `code` do DB sinh.
        TicketType t = ticketTypes.saveAndFlush(TicketType.create(event, req, clock.instant()));
        publishUpserted(t);
        return TicketTypeResponse.from(t);
    }

    @Transactional
    public TicketTypeResponse update(UUID id, TicketTypeRequest req) {
        TicketType t = get(id);
        Event event = event(req.eventId());
        if (ticketTypes.existsByEventIdAndNameIgnoreCaseAndIdNot(event.getId(), req.name().strip(), id)) {
            throw nameTaken();
        }
        if (t.everOnSale() && req.status() == TicketType.Status.DRAFT) {
            throw CatalogException.conflict("TICKET_TYPE_ALREADY_PUBLISHED", "Hạng vé đã mở bán, không chuyển về Nháp được");
        }
        if (t.everOnSale() && !t.getEvent().getId().equals(event.getId())) {
            throw CatalogException.conflict("TICKET_TYPE_ALREADY_PUBLISHED", "Hạng vé đã mở bán, không đổi sự kiện được");
        }
        UUID oldEventId = t.getEvent().getId();
        t.apply(event, req, clock.instant());
        if (!oldEventId.equals(event.getId())) {
            // Bản nháp đổi sự kiện: báo sự kiện cũ là hạng vé đã rời đi (key khác → partition khác).
            outbox.append(CatalogEvents.TOPIC, oldEventId, CatalogEvents.TICKET_TYPE_DELETED,
                    new CatalogEvents.TicketTypeDeleted(t.getId(), oldEventId));
        }
        publishUpserted(t);
        return TicketTypeResponse.from(t);
    }

    /** Chỉ xoá hẳn hạng vé chưa từng mở bán; đã mở bán → 409, UI đề nghị chuyển Tạm dừng (giữ lịch sử đơn). */
    @Transactional
    public void delete(UUID id) {
        TicketType t = get(id);
        if (t.everOnSale()) {
            throw CatalogException.conflict("TICKET_TYPE_ALREADY_PUBLISHED", "Hạng vé đã mở bán, chỉ có thể tạm dừng");
        }
        ticketTypes.delete(t);
        outbox.append(CatalogEvents.TOPIC, t.getEvent().getId(), CatalogEvents.TICKET_TYPE_DELETED,
                new CatalogEvents.TicketTypeDeleted(t.getId(), t.getEvent().getId()));
    }

    private void publishUpserted(TicketType t) {
        outbox.append(CatalogEvents.TOPIC, t.getEvent().getId(), CatalogEvents.TICKET_TYPE_UPSERTED,
                new CatalogEvents.TicketTypeUpserted(t.getId(), t.getEvent().getId(), t.getName(), t.getPrice(),
                        t.getTotalQuantity(), t.getStatus().name(), t.getUpdatedAt()));
    }

    private TicketType get(UUID id) {
        return ticketTypes.findById(id).orElseThrow(
                () -> CatalogException.notFound("TICKET_TYPE_NOT_FOUND", "Không tìm thấy hạng vé"));
    }

    private Event event(UUID id) {
        return events.findById(id).orElseThrow(EventService::notFound);
    }

    private static CatalogException nameTaken() {
        return CatalogException.conflict("TICKET_TYPE_NAME_TAKEN", "Sự kiện đã có hạng vé cùng tên");
    }
}
