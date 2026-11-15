package com.unbutu.ticket.catalog.event;

import java.util.UUID;

// DTO trả ra API, tách khỏi entity để đổi schema DB không làm vỡ API.
public record EventResponse(UUID id, String title, Event.Type type, String description) {

    static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getType(), event.getDescription());
    }
}
