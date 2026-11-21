package com.unbutu.ticket.inventory.reservation;

import com.unbutu.ticket.inventory.stock.StockKeys;
import com.unbutu.ticket.inventory.stock.StockStore;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trả vé của đơn giữ quá hạn mà không ai nhả (booking chết, khách bỏ ngang). release.lua idempotent → nhiều instance
 * cùng quét không sao. Chỉ quét sự kiện đang có hold; mỗi lượt tối đa 500 hold / sự kiện.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class HoldSweeper {

    private final StringRedisTemplate redis;
    private final StockStore store;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${inventory.hold.sweep-interval:1s}")
    void sweep() {
        Set<String> events = redis.opsForSet().members(StockKeys.EVENTS_WITH_HOLDS);
        if (events == null) {
            return;
        }
        var now = clock.instant();
        for (String e : events) {
            UUID eventId = UUID.fromString(e);
            int released = 0;
            for (UUID reservationId : store.expiredHolds(eventId, now, 500)) {
                if (store.release(eventId, reservationId)) {
                    released++;
                }
            }
            if (released > 0) {
                log.info("Hết hạn giữ vé: trả {} đơn của sự kiện {}", released, eventId);
            }
            if (!store.hasHolds(eventId)) {
                redis.opsForSet().remove(StockKeys.EVENTS_WITH_HOLDS, e);
            }
        }
    }
}
