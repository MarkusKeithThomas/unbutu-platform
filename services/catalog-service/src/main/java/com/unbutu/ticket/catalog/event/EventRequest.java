package com.unbutu.ticket.catalog.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Body tạo/sửa sự kiện (admin). PUT gửi đầy đủ các trường. hidden null = false (hiện cho khách). */
public record EventRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull Event.Type type,
        @Size(max = 2000) String description,
        @Size(max = 255) String venue,
        Instant startsAt,
        Boolean hidden) {
}
