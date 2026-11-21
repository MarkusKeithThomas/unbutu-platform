package com.unbutu.ticket.catalog.tickettype;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body tạo/sửa hạng vé (admin). PUT gửi đầy đủ các trường. */
public record TicketTypeRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(1_000_000_000) long price,          // VND
        @Min(0) @Max(1_000_000) int totalQuantity,
        @NotNull TicketType.Status status) {
}
