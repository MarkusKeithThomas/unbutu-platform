package com.unbutu.ticket.booking.pricing;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bản sao giá + thông tin sự kiện từ catalog (qua catalog.event.v1). Snapshot có version → chỉ nhận bản mới hơn. */
@Repository
@RequiredArgsConstructor
public class PriceBook {

    public record Price(UUID ticketTypeId, UUID eventId, String name, long price, String status) {
    }

    public record EventInfo(UUID eventId, String title, String venue, Instant startsAt) {
    }

    private final JdbcClient jdbc;

    void upsertPrice(UUID ticketTypeId, UUID eventId, String name, long price, String status, Instant version) {
        jdbc.sql("""
                        insert into ticket_type_prices (ticket_type_id, event_id, name, price, status, catalog_updated_at)
                        values (:tt, :event, :name, :price, :status, :ver)
                        on conflict (ticket_type_id) do update
                           set event_id = excluded.event_id, name = excluded.name, price = excluded.price,
                               status = excluded.status, deleted = false, catalog_updated_at = excluded.catalog_updated_at
                         where ticket_type_prices.catalog_updated_at < excluded.catalog_updated_at""")
                .param("tt", ticketTypeId).param("event", eventId).param("name", name).param("price", price)
                .param("status", status).param("ver", Timestamp.from(version))
                .update();
    }

    void markDeleted(UUID ticketTypeId) {
        jdbc.sql("update ticket_type_prices set deleted = true where ticket_type_id = :tt").param("tt", ticketTypeId).update();
    }

    void upsertEvent(UUID eventId, String title, String venue, Instant startsAt, Instant version) {
        jdbc.sql("""
                        insert into event_info (event_id, title, venue, starts_at, catalog_updated_at)
                        values (:id, :title, :venue, :starts, :ver)
                        on conflict (event_id) do update
                           set title = excluded.title, venue = excluded.venue, starts_at = excluded.starts_at,
                               catalog_updated_at = excluded.catalog_updated_at
                         where event_info.catalog_updated_at < excluded.catalog_updated_at""")
                .param("id", eventId).param("title", title).param("venue", venue)
                .param("starts", startsAt == null ? null : Timestamp.from(startsAt)).param("ver", Timestamp.from(version))
                .update();
    }

    /** Giá hiện hành của các hạng vé (chưa xoá). Thiếu hạng nào → không có trong kết quả. */
    public List<Price> prices(Collection<UUID> ticketTypeIds) {
        return jdbc.sql("""
                        select ticket_type_id, event_id, name, price, status from ticket_type_prices
                        where ticket_type_id in (:ids) and not deleted""")
                .param("ids", ticketTypeIds)
                .query((rs, i) -> new Price(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getLong(4), rs.getString(5)))
                .list();
    }

    public Optional<EventInfo> event(UUID eventId) {
        return events(List.of(eventId)).stream().findFirst();
    }

    public List<EventInfo> events(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select event_id, title, venue, starts_at from event_info where event_id in (:ids)")
                .param("ids", eventIds)
                .query((rs, i) -> new EventInfo(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()))
                .list();
    }
}
