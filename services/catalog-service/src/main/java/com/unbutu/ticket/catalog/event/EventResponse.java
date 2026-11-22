package com.unbutu.ticket.catalog.event;

import java.time.Instant;
import java.util.UUID;

// DTO trả ra API, tách khỏi entity để đổi schema DB không làm vỡ API.
public record EventResponse(UUID id, String title, Event.Type type, String description, String venue,
                            Instant startsAt, boolean hidden, Instant updatedAt) {

    static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getType(), event.getDescription(),
                event.getVenue(), event.getStartsAt(), event.isHidden(), event.getUpdatedAt());
    }
}
