package com.unbutu.ticket.catalog.event;

import java.time.Instant;
import java.util.UUID;

/** 1 thẻ sự kiện trên trang danh sách của khách: kèm giá thấp nhất của các hạng vé đang hiển thị. */
public record EventSummary(UUID id, String title, Event.Type type, String venue, Instant startsAt, long priceFrom) {
}
