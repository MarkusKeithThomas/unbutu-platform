package com.unbutu.ticket.catalog.event;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ghi sự kiện – chỉ ADMIN (SecurityConfig). Danh sách/chi tiết dùng API công khai {@code GET /api/catalog/events}. */
@RestController
@RequestMapping("/api/catalog/admin/events")
@RequiredArgsConstructor
class AdminEventController {

    private final EventService events;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventResponse create(@Valid @RequestBody EventRequest req) {
        return EventResponse.from(events.create(req));
    }

    @PutMapping("/{id}")
    EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventRequest req) {
        return EventResponse.from(events.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        events.delete(id);
    }
}
