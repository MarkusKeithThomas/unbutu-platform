package com.unbutu.ticket.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đẩy outbox → Redpanda. Giao "ít nhất 1 lần": gửi xong mới đánh dấu; chết giữa chừng → lần sau gửi lại
 * (consumer bỏ trùng theo eventId).
 *
 * <p>Nhiều instance của 1 service: {@code pg_try_advisory_xact_lock} → chỉ 1 relay chạy mỗi lúc (giữ thứ tự theo id,
 * không gửi trùng song song); instance khác bỏ lượt. ~vài nghìn event/s – đủ cho catalog, payment. Service ghi nhiều (booking) khi lên
 * hàng chục nghìn event/s: chia outbox theo partition/khoảng key cho nhiều relay, hoặc CDC (Debezium) đọc WAL.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final int BATCH = 500;
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(30);

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final KafkaAdmin admin;
    private final List<NewTopic> topics;
    private final Clock clock;
    private final String lockKey;

    public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, KafkaAdmin admin, List<NewTopic> topics,
                       Clock clock, String serviceName) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.admin = admin;
        this.topics = topics;
        this.clock = clock;
        this.lockKey = "outbox.relay:" + serviceName;
    }

    // Topic tạo lúc khởi động có thể hụt (broker chưa lên) → tạo lại trước lần gửi đầu (broker tắt auto-create).
    private boolean topicsReady;

    // Broker chết → không gõ lại mỗi 500 ms (log rác, giữ transaction vô ích): lùi dần tới 30 s.
    private Instant nextAttempt = Instant.EPOCH;
    private Duration backoff = Duration.ofSeconds(1);

    private record Row(long id, String topic, String key, String payload) {
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval:500ms}")
    @Transactional
    public void publishPending() {
        if (clock.instant().isBefore(nextAttempt)) {
            return;
        }
        Boolean leader = jdbc.sql("select pg_try_advisory_xact_lock(hashtext(:k))").param("k", lockKey)
                .query(Boolean.class).single();
        if (!Boolean.TRUE.equals(leader)) {
            return;
        }
        List<Row> rows = jdbc.sql("""
                        select id, topic, msg_key, payload::text as payload from outbox
                        where published_at is null order by id limit :batch""")
                .param("batch", BATCH)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("topic"), rs.getString("msg_key"), rs.getString("payload")))
                .list();
        if (rows.isEmpty()) {
            return;
        }
        try {
            // Gửi cả lô (producer gom batch), rồi chờ tất cả được broker xác nhận (acks=all).
            if (!topicsReady && !topics.isEmpty()) {
                admin.createOrModifyTopics(topics.toArray(NewTopic[]::new));
                topicsReady = true;
            }
            List<CompletableFuture<SendResult<String, String>>> sent = new ArrayList<>(rows.size());
            for (Row r : rows) {
                sent.add(kafka.send(r.topic(), r.key(), r.payload()));
            }
            CompletableFuture.allOf(sent.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            nextAttempt = clock.instant().plus(backoff);
            log.warn("Outbox: gửi {} event lỗi, thử lại sau {}s: {}", rows.size(), backoff.toSeconds(), e.toString());
            backoff = backoff.multipliedBy(2).compareTo(BACKOFF_MAX) > 0 ? BACKOFF_MAX : backoff.multipliedBy(2);
            return;   // không đánh dấu → lần sau gửi lại (có thể trùng → consumer dedupe)
        }
        jdbc.sql("update outbox set published_at = now() where id in (:ids)")
                .param("ids", rows.stream().map(Row::id).toList())
                .update();
        backoff = Duration.ofSeconds(1);
        log.info("Outbox: đã gửi {} event (id {}..{})", rows.size(), rows.getFirst().id(), rows.getLast().id());
    }

    /** Giữ 7 ngày cho việc tra cứu/điều tra; event log thật nằm ở Redpanda. */
    @Scheduled(cron = "${outbox.cleanup-cron:0 17 3 * * *}")
    @Transactional
    public void cleanup() {
        int n = jdbc.sql("delete from outbox where published_at < now() - interval '7 days'").update();
        if (n > 0) {
            log.info("Outbox: xoá {} event đã gửi > 7 ngày", n);
        }
    }
}
