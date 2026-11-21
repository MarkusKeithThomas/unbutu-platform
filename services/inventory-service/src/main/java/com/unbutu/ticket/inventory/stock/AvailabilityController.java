package com.unbutu.ticket.inventory.stock;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A8b – "Còn x / y" trên trang chọn vé: ĐƯỜNG ĐỌC NÓNG NHẤT lúc mở bán (mọi người đang xem đều poll).
 * 50k rps: response giống nhau cho mọi người + cache CDN 1 s → origin chỉ ~1 req/s/edge/sự kiện; origin đọc Redis
 * 1 round-trip (không chạm Postgres). Bước sau: Redis read replica.
 */
@RestController
@RequiredArgsConstructor
class AvailabilityController {

    record Availability(UUID ticketTypeId, int available, int total, String state) {
    }

    private static final CacheControl PUBLIC_1S = CacheControl.maxAge(Duration.ofSeconds(1)).cachePublic()
            .staleWhileRevalidate(Duration.ofSeconds(2)).staleIfError(Duration.ofSeconds(30));

    private final StockStore store;

    @Value("${inventory.low-stock-ratio:0.2}")
    private double lowRatio;

    @GetMapping("/api/inventory/events/{eventId}/availability")
    ResponseEntity<List<Availability>> availability(@PathVariable UUID eventId) {
        var list = store.read(eventId).stream()
                .filter(s -> "ON_SALE".equals(s.status()) || "PAUSED".equals(s.status()))   // không lộ bản nháp
                .map(s -> new Availability(s.ticketTypeId(), s.available(), s.total(), state(s)))
                .sorted(Comparator.comparing(Availability::ticketTypeId))
                .toList();
        return ResponseEntity.ok().cacheControl(PUBLIC_1S).body(list);
    }

    private String state(StockStore.TypeStock s) {
        if ("PAUSED".equals(s.status())) return "PAUSED";
        if (s.available() <= 0) return "SOLD_OUT";
        return s.available() < s.total() * lowRatio ? "LOW" : "ON_SALE";
    }
}
