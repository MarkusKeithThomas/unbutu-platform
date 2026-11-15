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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
