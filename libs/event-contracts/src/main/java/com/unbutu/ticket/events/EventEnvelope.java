package com.unbutu.ticket.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Vỏ chung của mọi event trên Redpanda (Kafka API).
 *
 * <ul>
 *   <li>{@code eventId}: id duy nhất của lần phát – consumer lưu lại để bỏ qua bản trùng (at-least-once ⇒ có thể nhận 2 lần).</li>
 *   <li>{@code type}: tên event, vd. {@code ticket-type.upserted}; consumer không biết type thì bỏ qua (tương thích tiến).</li>
 *   <li>{@code occurredAt}: lúc nghiệp vụ xảy ra (không phải lúc gửi).</li>
 * </ul>
 * Kafka key = id aggregate gốc (vd. eventId của sự kiện) → mọi event của 1 sự kiện vào cùng partition, đúng thứ tự.
 * Đổi schema: chỉ THÊM field (optional), không đổi/xoá – consumer cũ vẫn đọc được.
 */
public record EventEnvelope<T>(UUID eventId, String type, Instant occurredAt, T payload) {
}
