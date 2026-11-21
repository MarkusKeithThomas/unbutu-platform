package com.unbutu.ticket.booking.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {

    private BookingDtos() {
    }

    /** A9 – chỉ gửi hạng vé + số lượng. Giá do server tính từ bảng giá sao chép (không tin giá client). */
    public record CreateBookingRequest(@NotNull UUID eventId, @NotEmpty @Size(max = 4) List<@Valid Item> items) {
        public record Item(@NotNull UUID ticketTypeId, @Min(1) @Max(4) int quantity) {
        }
    }

    /** A10 – bước 2: thông tin người mua + phương thức → URL cổng thanh toán. Bấm lại → cùng URL (payment idempotent). */
    public record CheckoutRequest(@NotNull @Pattern(regexp = "VNBANK|VNPAYQR|INTCARD") String method, @NotNull @Valid Buyer buyer) {
        public record Buyer(@NotBlank @Size(max = 100) String name, @NotBlank @Email @Size(max = 255) String email,
                            @Pattern(regexp = "^$|^[0-9+ ]{8,20}$") String phone) {
        }
    }

    public record CheckoutResponse(String paymentUrl) {
    }

    public record EventView(UUID id, String title, String venue, Instant startsAt) {
    }

    public record Line(UUID ticketTypeId, String name, long unitPrice, int quantity, long amount) {
    }

    public record BookingView(UUID id, String code, String status, String cancelReason, EventView event, List<Line> lines,
                              int ticketCount, long subtotal, long fee, long total, Instant expiresAt, Instant createdAt) {
    }

    public record BookingPage(List<BookingView> items, int page, int size, boolean hasNext) {
    }
}
