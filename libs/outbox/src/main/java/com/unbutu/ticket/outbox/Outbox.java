package com.unbutu.ticket.outbox;

import com.unbutu.ticket.events.EventEnvelope;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ghi event vào bảng outbox TRONG transaction nghiệp vụ đang chạy (MANDATORY: gọi ngoài transaction → lỗi ngay).
 * Commit cùng lúc với thay đổi dữ liệu; rollback thì event cũng mất → DB và event log không bao giờ lệch nhau.
 */
public class Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public Outbox(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, UUID key, String type, Object payload) {
        var envelope = new EventEnvelope<>(UUID.randomUUID(), type, clock.instant(), payload);
        jdbc.sql("""
                        insert into outbox (event_id, topic, msg_key, type, payload, occurred_at)
                        values (:eventId, :topic, :key, :type, cast(:payload as jsonb), :occurredAt)""")
                .param("eventId", envelope.eventId())
                .param("topic", topic)
                .param("key", key.toString())
                .param("type", type)
                .param("payload", json.writeValueAsString(envelope))
                .param("occurredAt", Timestamp.from(envelope.occurredAt()))
                .update();
    }
}
