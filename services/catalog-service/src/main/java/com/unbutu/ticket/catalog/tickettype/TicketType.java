package com.unbutu.ticket.catalog.tickettype;

import com.unbutu.ticket.catalog.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Hạng vé của 1 sự kiện: tên, giá, tổng số vé. Số đã bán/còn lại do inventory-service sở hữu. */
@Entity
@Table(name = "ticket_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketType {

    public enum Status { DRAFT, ON_SALE, PAUSED }

    @Id
    private UUID id;

    /** TT-0001… – DB sinh từ sequence khi INSERT; Hibernate đọc lại sau insert. */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Lần đầu chuyển sang ON_SALE. Khác null → có thể đã có đơn → không cho xoá hẳn (chỉ tạm dừng). */
    @Column(name = "first_on_sale_at")
    private Instant firstOnSaleAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static TicketType create(Event event, TicketTypeRequest req, Instant now) {
        TicketType t = new TicketType();
        t.id = UUID.randomUUID();
        t.createdAt = now;
        t.apply(event, req, now);
        return t;
    }

    void apply(Event event, TicketTypeRequest req, Instant now) {
        this.event = event;
        this.name = req.name().strip();
        this.price = req.price();
        this.totalQuantity = req.totalQuantity();
        this.status = req.status();
        if (status == Status.ON_SALE && firstOnSaleAt == null) {
            firstOnSaleAt = now;
        }
        this.updatedAt = now;
    }

    public boolean everOnSale() {
        return firstOnSaleAt != null;
    }
}
