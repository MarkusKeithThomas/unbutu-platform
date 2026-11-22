package com.unbutu.ticket.catalog.event;

import com.unbutu.ticket.catalog.tickettype.TicketType;
import com.unbutu.ticket.catalog.tickettype.TicketTypeResponse;
import com.unbutu.ticket.catalog.tickettype.TicketTypeService;
import com.unbutu.ticket.catalog.web.SliceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import com.unbutu.ticket.catalog.cache.CatalogCache;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API công khai cho khách (không cần đăng nhập) – đường đọc nóng nhất của catalog.
 *
 * <p>Mở rộng 50k rps: response giống nhau cho mọi người + {@code Cache-Control: public} → CDN/gateway cache
 * gánh gần hết (origin chỉ nhận ~1 req / URL / 30 s / edge). URL không chứa thời gian hay user để cache trúng.
 * Không có CDN (Funnel → gateway) → cache trong app: {@link CatalogCache} (Caffeine 5 s + Redis 30 s, xoá khi admin ghi) –
 * trúng cache = trả chuỗi JSON có sẵn, không JPA/DB/serialize. Bước sau nếu cần: read replica.
 */
@RestController
@RequestMapping("/api/catalog/events")
@RequiredArgsConstructor   // @Min/@Max trên tham số: Spring MVC tự validate → 400 Problem Details
class EventController {

    /** Dữ liệu catalog đổi rất ít: khách thấy giá/hạng vé mới chậm tối đa ~30 s (+30 s stale khi origin chậm/lỗi). */
    private static final CacheControl PUBLIC_30S = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()
            .staleWhileRevalidate(Duration.ofSeconds(30)).staleIfError(Duration.ofMinutes(5));

    private static final int SHARED_PAGES = 10;   // chỉ 10 trang đầu vào Redis (số key giới hạn – Redis noeviction dùng chung)

    private final EventRepository events;
    private final CatalogCache cache;
    private final JsonMapper json;
    private final EventService eventService;
    private final TicketTypeService ticketTypes;
    private final Clock clock;

    /** Sự kiện sắp diễn ra đang mở bán, sớm nhất trước. size giới hạn → ít biến thể URL, DB đọc tối đa 51 dòng. */
    @GetMapping
    ResponseEntity<String> list(@RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
                                @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        String body = cache.get("list:" + page + ":" + size, page < SHARED_PAGES, () -> {
            // "now" làm tròn phút: request trong cùng phút cho cùng kết quả (khớp với cache), không lấy từ client.
            var now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
            var slice = events.findUpcomingOnSale(now, TicketType.VISIBLE, PageRequest.of(page, size));
            return json.writeValueAsString(SliceResponse.of(slice, s -> s));
        });
        return ok(body);
    }

    @GetMapping("/{id}")
    ResponseEntity<String> get(@PathVariable UUID id) {
        // 404 ném từ loader → không cache (id ngẫu nhiên không làm đầy cache); chỉ sự kiện có thật mới vào Redis.
        return ok(cache.get("event:" + id, true, () -> json.writeValueAsString(EventResponse.from(eventService.get(id)))));
    }

    /** Hạng vé người mua thấy được: đang bán hoặc tạm dừng (không lộ bản nháp), giá cao trước. */
    @GetMapping("/{id}/ticket-types")
    ResponseEntity<String> ticketTypes(@PathVariable UUID id) {
        return ok(cache.get("tt:" + id, true, () -> {
            eventService.get(id);   // 404 nếu sự kiện không tồn tại (PK lookup) → không cache
            return json.writeValueAsString(ticketTypes.listVisible(id));
        }));
    }

    private static ResponseEntity<String> ok(String json) {
        return ResponseEntity.ok().cacheControl(PUBLIC_30S).contentType(MediaType.APPLICATION_JSON).body(json);
    }
}
