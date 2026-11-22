package com.unbutu.ticket.catalog.event;

import com.unbutu.ticket.catalog.tickettype.TicketType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Admin: mọi sự kiện, mới tạo trước. */
    List<Event> findAllByOrderByCreatedAtDesc();

    /**
     * Khách: sự kiện chưa diễn ra, không ẩn, và có ít nhất 1 hạng vé đang hiển thị, kèm giá thấp nhất – 1 query, không N+1.
     * Dùng index ticket_types(event_id, status, price) + events(starts_at). Slice → không COUNT.
     */
    @Query("""
            select new com.unbutu.ticket.catalog.event.EventSummary(e.id, e.title, e.type, e.venue, e.startsAt, min(t.price))
            from TicketType t join t.event e
            where t.status in :statuses and e.hidden = false and (e.startsAt is null or e.startsAt >= :from)
            group by e.id, e.title, e.type, e.venue, e.startsAt
            order by e.startsAt asc nulls last, e.id asc
            """)
    Slice<EventSummary> findUpcomingOnSale(Instant from, Collection<TicketType.Status> statuses, Pageable pageable);
}
