package com.unbutu.ticket.catalog.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA cần constructor rỗng, nhưng code nghiệp vụ không được dùng
public class Event {

    public enum Type { MOVIE, CONCERT, SPORT, THEATER }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String description;

    private String venue;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static Event create(EventRequest req, Instant now) {
        Event e = new Event();
        e.id = UUID.randomUUID();
        e.createdAt = now;
        e.apply(req, now);
        return e;
    }

    void apply(EventRequest req, Instant now) {
        this.title = req.title().strip();
        this.type = req.type();
        this.description = blankToNull(req.description());
        this.venue = blankToNull(req.venue());
        this.startsAt = req.startsAt();
        this.updatedAt = now;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
