package com.unbutu.ticket.catalog.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache response JSON của API công khai catalog – 2 tầng:
 * <ol>
 *   <li><b>Caffeine</b> trong bộ nhớ (mỗi instance, {@code local-ttl} 5 s): trúng → không DB, không JPA, không serialize JSON.
 *       {@code get(key, loader)} chỉ cho 1 luồng nạp mỗi key → cache hết hạn không dồn cả nghìn request vào DB.</li>
 *   <li><b>Redis</b> dùng chung giữa các instance ({@code redis-ttl} 30 s): instance mới / tầng 1 hết hạn vẫn không chạm DB.</li>
 * </ol>
 * Admin ghi (sự kiện / hạng vé) → {@link #invalidateAllAfterCommit()} xoá cả 2 tầng SAU khi commit. Ghi hiếm → xoá hết cho đơn giản,
 * đúng hơn xoá từng key (hạng vé đổi sự kiện, ẩn/hiện ảnh hưởng danh sách…). Instance khác: tầng 1 cũ tối đa {@code local-ttl}.
 * <p>
 * Redis prod dùng chung với inventory ở chế độ {@code noeviction} (đầy = inventory lỗi giữ vé) → chỉ đưa vào Redis key có số
 * lượng GIỚI HẠN (người gọi quyết định qua {@code shared}: 10 trang danh sách đầu, sự kiện có thật) và luôn có TTL.
 * Redis lỗi/chậm (timeout 200 ms) → bỏ qua tầng 2, đọc DB – API không phụ thuộc Redis.
 */
@Slf4j
@Component
public class CatalogCache {

    private static final String PREFIX = "catalog:cache:v1:";
    private static final String INDEX = PREFIX + "keys";   // tập các key đã ghi → xoá hết khi admin ghi

    private final boolean enabled;
    private final Duration redisTtl;
    private final StringRedisTemplate redis;
    private final Cache<String, String> local;

    CatalogCache(StringRedisTemplate redis,
                 @Value("${catalog.cache.enabled}") boolean enabled,
                 @Value("${catalog.cache.local-ttl}") Duration localTtl,
                 @Value("${catalog.cache.redis-ttl}") Duration redisTtl) {
        this.redis = redis;
        this.enabled = enabled;
        this.redisTtl = redisTtl;
        this.local = Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(localTtl).build();
    }

    /**
     * @param shared true = được ghi vào Redis (key có số lượng giới hạn); false = chỉ tầng 1 (giới hạn bởi maximumSize)
     * @param loader đọc DB + serialize JSON; ném lỗi (vd. 404) → không cache gì
     */
    public String get(String key, boolean shared, Supplier<String> loader) {
        if (!enabled) {
            return loader.get();
        }
        return local.get(key, k -> {
            String v = shared ? redisGet(k) : null;
            if (v == null) {
                v = loader.get();
                if (shared) {
                    redisSet(k, v);
                }
            }
            return v;
        });
    }

    /** Gọi trong transaction ghi của admin: xoá sau commit (xoá trước commit → request khác kịp nạp lại dữ liệu cũ). */
    public void invalidateAllAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateAll();
                }
            });
        } else {
            invalidateAll();
        }
    }

    void invalidateAll() {
        local.invalidateAll();
        try {
            Set<String> keys = redis.opsForSet().members(INDEX);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete(INDEX);
        } catch (RuntimeException e) {
            log.warn("Không xoá được cache catalog trên Redis (tự hết hạn sau {}): {}", redisTtl, e.getMessage());
        }
    }

    private String redisGet(String key) {
        try {
            return redis.opsForValue().get(PREFIX + key);
        } catch (RuntimeException e) {
            log.debug("Redis lỗi khi đọc cache {} – đọc DB: {}", key, e.getMessage());
            return null;
        }
    }

    private void redisSet(String key, String value) {
        try {
            redis.opsForValue().set(PREFIX + key, value, redisTtl);
            redis.opsForSet().add(INDEX, PREFIX + key);
            redis.expire(INDEX, redisTtl.multipliedBy(2));
        } catch (RuntimeException e) {
            log.debug("Redis lỗi khi ghi cache {}: {}", key, e.getMessage());
        }
    }
}
