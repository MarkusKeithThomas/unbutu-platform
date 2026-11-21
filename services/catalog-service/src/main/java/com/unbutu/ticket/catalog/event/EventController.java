package com.unbutu.ticket.catalog.event;

import com.unbutu.ticket.catalog.tickettype.TicketTypeResponse;
import com.unbutu.ticket.catalog.tickettype.TicketTypeService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API công khai (không cần đăng nhập). */
@RestController
@RequestMapping("/api/catalog/events")
@RequiredArgsConstructor
class EventController {

    private final EventRepository events;
    private final EventService eventService;
    private final TicketTypeService ticketTypes;

    @GetMapping
    List<EventResponse> list() {
        return events.findAllByOrderByCreatedAtDesc().stream().map(EventResponse::from).toList();
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(eventService.get(id));
    }

    /** Hạng vé người mua thấy được: đang bán hoặc tạm dừng (không lộ bản nháp). */
    @GetMapping("/{id}/ticket-types")
    List<TicketTypeResponse.Public> ticketTypes(@PathVariable UUID id) {
        eventService.get(id);   // 404 nếu sự kiện không tồn tại
        return ticketTypes.listVisible(id);
    }
}
