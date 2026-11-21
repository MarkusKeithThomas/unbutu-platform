package com.unbutu.ticket.inventory.stock;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Sổ cái Postgres – nguồn sự thật bền của tồn kho. */
@Repository
@RequiredArgsConstructor
public class StockLedger {

    public record Row(UUID ticketTypeId, int totalQuantity, int sold, String status, long versionMillis) {
        public int available() {
            return Math.max(0, totalQuantity - sold);
        }
    }

    private final JdbcClient jdbc;

    /** Upsert snapshot từ catalog; bản cũ hơn bản đang có → bỏ qua (event đến trễ / replay). */
    public void upsert(UUID ticketTypeId, UUID eventId, int totalQuantity, String status, Instant catalogUpdatedAt) {
        jdbc.sql("""
                        insert into stock (ticket_type_id, event_id, total_quantity, status, catalog_updated_at)
                        values (:tt, :event, :total, :status, :ver)
                        on conflict (ticket_type_id) do update
                           set event_id = excluded.event_id, total_quantity = excluded.total_quantity,
                               status = excluded.status, deleted = false,
                               catalog_updated_at = excluded.catalog_updated_at, updated_at = now()
                         where stock.catalog_updated_at < excluded.catalog_updated_at""")
                .param("tt", ticketTypeId).param("event", eventId).param("total", totalQuantity)
                .param("status", status).param("ver", Timestamp.from(catalogUpdatedAt))
                .update();
    }

    public void markDeleted(UUID ticketTypeId) {
        jdbc.sql("update stock set deleted = true, updated_at = now() where ticket_type_id = :tt")
                .param("tt", ticketTypeId).update();
    }

    public int sold(UUID ticketTypeId) {
        return jdbc.sql("select sold from stock where ticket_type_id = :tt").param("tt", ticketTypeId)
                .query(Integer.class).optional().orElse(0);
    }

    public boolean isCommitted(UUID reservationId) {
        return jdbc.sql("select 1 from commits where reservation_id = :r").param("r", reservationId)
                .query(Integer.class).optional().isPresent();
    }

    public void recordCommit(UUID reservationId, UUID eventId, boolean late) {
        jdbc.sql("insert into commits (reservation_id, event_id, late) values (:r, :e, :late)")
                .param("r", reservationId).param("e", eventId).param("late", late).update();
    }

    /** Chốt bán – CHỐT CHẶN CUỐI chống oversell: chỉ cộng khi sold + qty ≤ tổng. false → không đủ (caller rollback). */
    public boolean addSold(UUID ticketTypeId, int qty) {
        return jdbc.sql("""
                        update stock set sold = sold + :q, updated_at = now()
                        where ticket_type_id = :tt and not deleted and sold + :q <= total_quantity""")
                .param("q", qty).param("tt", ticketTypeId).update() == 1;
    }

    /** Hạng vé còn hiệu lực của 1 sự kiện (index ix_stock_event). */
    public List<Row> forEvent(UUID eventId) {
        return jdbc.sql("""
                        select ticket_type_id, total_quantity, sold, status, catalog_updated_at
                        from stock where event_id = :event and not deleted""")
                .param("event", eventId)
                .query((rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant().toEpochMilli()))
                .list();
    }
}
