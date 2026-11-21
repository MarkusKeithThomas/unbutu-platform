package com.unbutu.ticket.inventory.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * booking gọi khi khách bấm "Tiếp tục thanh toán". reservationId = bookingId (idempotent: gọi lại → kết quả cũ).
 * Giới hạn 4 vé / tài khoản / sự kiện do booking kiểm (nó biết user); ở đây chỉ chặn trên cho an toàn.
 */
public record ReservationRequest(
        @NotNull UUID reservationId,
        @NotEmpty @Size(max = 10) List<@Valid Item> items,
        @Min(30) @Max(1800) Integer ttlSeconds) {

    public record Item(@NotNull UUID ticketTypeId, @Min(1) @Max(10) int quantity) {
    }
}
