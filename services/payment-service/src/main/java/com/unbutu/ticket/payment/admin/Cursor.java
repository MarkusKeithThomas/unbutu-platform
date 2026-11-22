package com.unbutu.ticket.payment.admin;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/** Con trỏ keyset (created_at, id) – chuỗi mờ cho client, không OFFSET. */
record Cursor(Instant createdAt, UUID id) {

    String encode() {
        String raw = createdAt.truncatedTo(ChronoUnit.MICROS) + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** null nếu rỗng; sai định dạng → IllegalArgumentException (400). */
    static Cursor decode(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] p = new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8).split("\\|", 2);
        if (p.length != 2) {
            throw new IllegalArgumentException("cursor không hợp lệ");
        }
        return new Cursor(Instant.parse(p[0]), UUID.fromString(p[1]));
    }
}
