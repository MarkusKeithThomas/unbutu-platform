package com.unbutu.ticket.catalog.event;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog/events")
@RequiredArgsConstructor
class EventController {

    private final EventRepository events;

    @GetMapping
    List<EventResponse> list() {
        return events.findAllByOrderByCreatedAtDesc().stream().map(EventResponse::from).toList();
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable UUID id) {
        return events.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }
}
